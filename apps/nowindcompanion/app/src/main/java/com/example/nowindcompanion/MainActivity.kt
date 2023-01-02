package com.example.nowindcompanion

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nowindcompanion.ui.theme.NowindCompanionTheme
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import kotlin.random.Random
import androidx.compose.runtime.*

// kotlin: https://pl.kotl.in/xHBgsipa_

@Composable
fun DeviceCard(painter: Painter, info : DeviceInfo) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.5f)
            .padding(16.dp)
    )
    {
        ImageCard(
            painter = painter,
            contentDescription = "",
            title = info.version.toString()
        )
    }
    val serial : String = info.serial
    val description : String = info.description
    Text(text = "Serial number: '$serial'")
    Text(text = "Description  : '$description'")
}

@Composable
fun HomeScreen(viewModel: NowindViewModel) {
    val painter = painterResource(id = R.drawable.nowindv1)
    val painter2 = painterResource(id = R.drawable.nowindv2)
    Column(Modifier.fillMaxWidth()) {
        val info = viewModel.deviceInfo.value
        val version = info.version
        when (version) {
            DetectedNowindVersion.None -> Text(text = "Waiting...")
            DetectedNowindVersion.V1 -> DeviceCard(painter, info)
            DetectedNowindVersion.V2 -> DeviceCard(painter2, info)
        }
    }
}

@Composable
fun SettingScreen(viewModel: NowindViewModel) {
    Column( Modifier.fillMaxWidth() ) {
        val color = remember {
            mutableStateOf(Color.Yellow)
        }
        ColorBox(
            Modifier
                .weight(0.3f)
                .fillMaxWidth()
        )
        {
            color.value = it
            viewModel.write("color changes to $color.value!")
        }
        Box(
            modifier = Modifier
                .background(color.value)
                .weight(0.3f)
                .fillMaxWidth()
        )
    }
}

@Composable
fun DebugScreen(viewModel: NowindViewModel) {
    Box( Modifier.fillMaxWidth()){
        val messages = viewModel.messages.value
        LazyColumn {
            items(messages.data) { data ->
                Text(text = data)
            }
        }
    }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
fun Pager(viewModel: NowindViewModel) {
    HorizontalPager(
        count = 3)
    { page ->
        when (page) {
            0 -> HomeScreen(viewModel)
            1 -> SettingScreen(viewModel)
            2 -> DebugScreen(viewModel)
        }
    }
}

@Composable
fun FrontPage(viewModel: NowindViewModel) {
    NowindCompanionTheme {
        Surface(color = MaterialTheme.colors.background) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
                ) {
                Row {
                    Text( text = "Nowind Companion (C) Jan Wilmans",
                        modifier = Modifier
                            .background(Color.LightGray)
                            .requiredHeight(50.dp)
                            .fillMaxWidth()
                            //.weight(0.5f)
                    )
//                    Draw {
//                        drawCircle(
//                            color = Color.Red,
//                            radius = 50f,
//                            shape = CircleShape
//                        )
//                    }
                }
                // BottomAppBar try out

                Row(Modifier.weight(1f).fillMaxSize()) {
                    Pager(viewModel)
                }

//                Text( text = "foot",
//                    modifier = Modifier
//                        .background(Color.Blue)
//                        .requiredHeight(50.dp)
//                        .fillMaxWidth()
//                        .weight(2f)
//                )
            }
        }

    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i("tag", "Initialize nowind main")

        setContent {
            val viewModel = viewModel<NowindViewModel>()
            val connection = FTDIClient(this, viewModel)
            connection.getIncomingDataUpdates()
            FrontPage(viewModel)
        }
    }
}


class MainActivityExperimentColors : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NowindCompanionTheme {
                Column(
                    modifier = Modifier
                        .background(Color.Green)
                        .fillMaxHeight(0.5f)
                        .fillMaxWidth()
                        .border(5.dp, Color.Magenta)
                        .padding(16.dp)
                        .border(5.dp, Color.Blue)
                        .padding(16.dp)
                )
                {
                    Text("Hello", modifier = Modifier.clickable {

                    })
                    Text("World")
                    Text("Nowind")
                    Spacer(modifier = Modifier.height(50.dp))
                    Text("Interface")
                }
            }
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

