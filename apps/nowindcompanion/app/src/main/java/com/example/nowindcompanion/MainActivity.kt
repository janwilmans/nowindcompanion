package com.example.nowindcompanion

import NowindState
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.example.nowindcompanion.ui.theme.NowindCompanionTheme
import com.google.accompanist.pager.*
import kotlin.random.Random

// kotlin: https://pl.kotl.in/xHBgsipa_

@Composable
fun Logo(painter: Painter, text : String) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.5f)
            .padding(16.dp)
    )
    {
        ImageCard(
            painter = painter,
            contentDescription = "",
            title = text
        )
    }
}


@Composable
fun HomeScreen(state: NowindState) {
    val painter = painterResource(id = R.drawable.nowindv1)
    val painter2 = painterResource(id = R.drawable.nowindv2)
    Column() {
        var version = state.version
        when (version.value) {
            NowindState.DetectedNowindVersion.None -> Text(text = "Waiting...")
            NowindState.DetectedNowindVersion.V1 -> Logo(painter, version.value.toString())
            NowindState.DetectedNowindVersion.V2 -> Logo(painter2, version.value.toString())
        }
    }
}

@Composable
fun SettingScreen(state: NowindState) {
    Column(Modifier.fillMaxSize()) {
        val color = remember {
            mutableStateOf(Color.Yellow)
        }
        ColorBox(
            Modifier
                .weight(1f)
                .fillMaxSize()
        )
        {
            color.value = it
            state.write("color changes to $color.value!")
        }
        Box(
            modifier = Modifier
                .background(color.value)
                .weight(1f)
                .fillMaxSize()
        )
    }
}

@Composable
fun DebugScreen(state: NowindState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
    ){
        val messages = state.messages
        LazyColumn {
            items(messages.data) { data ->
                Text(text = data)
            }
        }
    }

}

@OptIn(ExperimentalPagerApi::class)
@Composable
fun FrontPage(state: NowindState) {
    NowindCompanionTheme {
        Row() {
            Text("dit is een header", style = TextStyle(color = Color.Black), fontSize = 16.sp)
        }
        Row() {
            HorizontalPager(count = 3)
            { page ->
                when(page) {
                    0-> HomeScreen(state)
                    1 -> SettingScreen(state)
                    2 -> DebugScreen(state)
                }
            }
        }
        Row() {
            Text("dit is een footer", style = TextStyle(color = Color.Black), fontSize = 16.sp)
        }

    }
}

class MainActivity : ComponentActivity() {

    var state = NowindState()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i("tag", "Initialize nowind main")
        val connection = FTDIClient(this, state)
        var data = connection.getIncomingDataUpdates();

        setContent {
            FrontPage(state)
        }
    }
}

data class Message(val author: String, val body: String)

@Composable
fun ImageCard(
    painter: Painter,
    contentDescription: String,
    title: String,
    modifier: Modifier = Modifier
  )
{
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        elevation = 5.dp
    )
    {
        Box(modifier = Modifier.height(200.dp))
        {
            Image(
                painter = painter,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit
            )
            Box(modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black
                        ),
                        startY = 300f
                    )
                ))
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
                contentAlignment = Alignment.BottomStart
            )
            {
                Text(title, style = TextStyle(color = Color.White), fontSize = 16.sp)
            }
        }
    }

}


@Composable
fun MessageCard(msg: Message) {
    Row(modifier = Modifier.padding(all = 8.dp)) {
        Image(
            painter = painterResource(R.drawable.nowind),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .border(1.5.dp, MaterialTheme.colors.secondary, CircleShape)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                text = msg.author,
                color = MaterialTheme.colors.secondaryVariant
            )

            Spacer(modifier = Modifier.height(4.dp))
            Text(text = msg.body)
        }
    }
}

@Preview
@Composable
fun DefaultPreview() {
    NowindCompanionTheme {
        Surface {
            MessageCard(
                msg = Message("Colleague", "Take a look at Jetpack Compose, it's great!")
            )
        }
    }
}


@Composable
fun ColorBox(modifier: Modifier = Modifier,
             updateColor: (Color) -> Unit
) {

    Box(modifier = modifier
        .background(Color.Red)
        .clickable {
            updateColor(
                Color(
                    Random.nextFloat(),
                    Random.nextFloat(),
                    Random.nextFloat(),
                    1f
                )
            )
        }
    )
}

