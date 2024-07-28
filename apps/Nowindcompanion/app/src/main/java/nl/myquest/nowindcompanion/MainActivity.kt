package nl.myquest.nowindcompanion

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import nl.myquest.nowindcompanion.ui.theme.NowindCompanionTheme
import kotlin.random.Random

class MainActivityExample : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NowindCompanionTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colorScheme.background
                ) {
                    Greeting("Android")
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    NowindCompanionTheme {
        Greeting("Android")
    }
}


@Composable
fun DeviceCard(painter: Painter, info: DeviceInfo) {
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
    val serial: String = info.serial
    val description: String = info.description
    Text(text = "Serial number: '$serial'")
    Text(text = "Description  : '$description'")
}

@Composable
fun HomeScreen(viewModel: NowindViewModel) {
    val painter = painterResource(id = R.drawable.nowindv1)
    val painter2 = painterResource(id = R.drawable.nowindv2)
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
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
    Column(Modifier.fillMaxWidth()) {
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
    Box(Modifier.fillMaxWidth()) {
        val messages = viewModel.messages.value
        LazyColumn {
            items(messages.data) { data ->
                Text(text = data)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Pager(viewModel: NowindViewModel) {
    val pagerState = rememberPagerState { 3 }
    HorizontalPager(
        state = pagerState, modifier = Modifier
            .fillMaxHeight()
    )
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
        Surface(color = colorScheme.background) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Row {
                    val buildNumber = 1 //BuildConfig.VERSION_CODE
                    Text(
                        text = "Nowind Companion (C) Jan Wilmans ($buildNumber)",
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

                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                ) {
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
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp)
    )
    {
        Box(modifier = Modifier.height(200.dp))
        {
            Image(
                painter = painter,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black
                            ),
                            startY = 300f
                        )
                    )
            )
            Box(
                modifier = Modifier
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
                .border(1.5.dp, MaterialTheme.colorScheme.secondary, CircleShape)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                text = msg.author,
                color = MaterialTheme.colorScheme.secondary
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
fun ColorBox(
    modifier: Modifier = Modifier,
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
