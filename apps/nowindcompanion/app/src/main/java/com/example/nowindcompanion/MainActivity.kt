package com.example.nowindcompanion

import android.content.res.AssetFileDescriptor
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.nowindcompanion.ui.theme.NowindCompanionTheme
import com.ftdi.j2xx.D2xxManager
import kotlin.random.Random


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i("tag", "Initialize nowind connect")
        val connection = FTDIClient(this)
        var data = connection.getIncomingDataUpdates();

        setContent {
            Column(Modifier.fillMaxSize()) {
                val color = remember {
                    mutableStateOf(Color.Yellow)
                }
                ColorBox(
                    Modifier.weight(1f).fillMaxSize()
                )
                {
                    color.value = it
                }
                Box(
                    modifier = Modifier
                        .background(color.value)
                        .weight(1f)
                        .fillMaxSize()
                )
            }

        }
    }
}

class MainActivityFTDI : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i("tag", "Initialize nowind main")
        val connection = FTDIClient(this)
        var data = connection.getIncomingDataUpdates();

        // Create TextView programmatically.
        val textView = TextView(this)

        // setting height and width
        textView.layoutParams = RelativeLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        // setting text
        textView.setText("GEEKSFORGEEKS")
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
        // onClick the text a message will be displayed "HELLO GEEK"
        textView.setOnClickListener()
        {
            Toast.makeText(
                this, "HELLO GEEK",
                Toast.LENGTH_LONG
            ).show()
        }

        // Add TextView to LinearLayout
        //layout ?.addView(textView)

        setContent {
            val painter = painterResource(id = R.drawable.nowindv1)
            val painter2 = painterResource(id = R.drawable.nowindv2)
            Column() {

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .padding(16.dp)
                )
                {
                    ImageCard(
                        painter = painter,
                        contentDescription = "Test content ipsum lorem",
                        title = "Nowind Interface V1"
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .padding(16.dp)
                ) {
                    ImageCard(
                        painter = painter2,
                        contentDescription = "Test content ipsum lorem",
                        title = "Nowind Interface V2"
                    )
                }
            }
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

