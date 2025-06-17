package com.nhshah.picturepuzzle
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.fonts.FontStyle
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cloudinary.Cloudinary
import com.cloudinary.utils.ObjectUtils
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.nhshah.picturepuzzle.ui.theme.PicturePuzzleTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

val darkBackground = Color(0xFF181818)
enum class Tab { HOME, MY_PUZZLES, SETTINGS }

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        setContent {
            PicturePuzzleTheme {
                var usernameState by remember { mutableStateOf("") }
                var isUsernameAvailable by remember { mutableStateOf(true) }
                var showPuzzleScreen by remember { mutableStateOf(false) }
                var isCheckingUsername by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    val storedUsername = sharedPreferences.getString("username", null)
                    val storedUserId = sharedPreferences.getString("userId", null)
                    if (storedUsername != null && storedUserId != null) {
                        val db = FirebaseFirestore.getInstance()
                        db.collection("users")
                            .document(storedUserId)
                            .get()
                            .addOnSuccessListener { document ->
                                if (document.exists()) {
                                    usernameState = storedUsername
                                    showPuzzleScreen = true
                                } else {
                                    sharedPreferences.edit().clear().apply()
                                    showPuzzleScreen = false
                                }
                                isCheckingUsername = false
                            }
                            .addOnFailureListener {
                                isCheckingUsername = false
                            }
                    } else {
                        isCheckingUsername = false
                    }
                }
                if (isCheckingUsername) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    if (!showPuzzleScreen) {
                        UsernameScreen(
                            usernameState = usernameState,
                            onUsernameChange = { usernameState = it },
                            isUsernameAvailable = isUsernameAvailable,
                            onCheckUsername = {
                                val db = FirebaseFirestore.getInstance()
                                val userDocRef = db.collection("users").document()
                                val userId = userDocRef.id
                                db.collection("users")
                                    .whereEqualTo("username", usernameState)
                                    .get()
                                    .addOnSuccessListener { result ->
                                        if (result.isEmpty) {
                                            isUsernameAvailable = true
                                            saveUserDataToFirebase(userId, usernameState)
                                            sharedPreferences.edit()
                                                .putString("userId", userId)
                                                .putBoolean("isUsernameSet", true)
                                                .putString("username", usernameState)
                                                .apply()
                                            showPuzzleScreen = true
                                        } else {
                                            isUsernameAvailable = false
                                        }
                                    }
                                    .addOnFailureListener {
                                    }
                            }
                        )
                    } else {
                        var selectedTab by remember { mutableStateOf(Tab.HOME) }
                        val sharedPreferences = getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                        val userId = sharedPreferences.getString("userId", "")
                        val username = sharedPreferences.getString("username", "")
                        Scaffold(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(darkBackground),
                            topBar = {
                                TopAppBar(
                                    title = {
                                        Text(
                                            text = "Picture Puzzle",
                                            style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center
                                        )
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            },
                            bottomBar = {
                                FooterBar(
                                    selectedTab = selectedTab,
                                    onTabSelected = { selectedTab = it }
                                )
                            }
                        ) { innerPadding ->
                            PicturePuzzleScreen(
                                modifier = Modifier.padding(innerPadding),
                                selectedTab = selectedTab,
                                usernameState = username.toString(),
                                userId = userId ?: ""
                            )
                        }
                    }
                }
            }
        }
    }

    private fun saveUserDataToFirebase(userId: String, username: String) {
        val db = FirebaseFirestore.getInstance()
        val userData = hashMapOf(
            "username" to username,
            "puzzlesOwned" to 0,
            "createdAt" to System.currentTimeMillis(),
        )
        db.collection("users").document(userId)
            .set(userData)
            .addOnSuccessListener {
                Log.d("Firebase", "User data saved successfully")
            }
            .addOnFailureListener { e ->
                Log.e("Firebase", "Error saving user data", e)
            }
    }
}

@Composable
fun FooterBar(
    selectedTab: Tab,
    onTabSelected: (Tab) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White)
            )
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FooterButton(
                    icon = Icons.Default.Home,
                    selected = selectedTab == Tab.HOME,
                    onClick = { onTabSelected(Tab.HOME) }
                )
                FooterButton(
                    icon = Icons.Default.Image,
                    selected = selectedTab == Tab.MY_PUZZLES,
                    onClick = { onTabSelected(Tab.MY_PUZZLES) }
                )
                FooterButton(
                    icon = Icons.Default.Settings,
                    selected = selectedTab == Tab.SETTINGS,
                    onClick = { onTabSelected(Tab.SETTINGS) }
                )
            }
        }
    }
}

@Composable
fun RowScope.FooterButton(
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = contentColor)
    }
}

@Composable
fun UserGreetingScreen(userName: String) {
    val db = FirebaseFirestore.getInstance()
    var puzzleData by remember { mutableStateOf<List<Triple<String, String, Long>>?>(null) }
    LaunchedEffect(userName) {
        try {
            val result = db.collection("puzzles")
                .whereEqualTo("created_by", userName)
                .get()
                .await()
            puzzleData = result.documents.map {
                val puzzleId = it.id
                val imageUrl = it.getString("image") ?: ""
                val timestamp = it.getLong("timestamp") ?: 0L
                Triple(puzzleId, imageUrl, timestamp)
            }
        } catch (e: Exception) {
            Log.e("UserGreetingScreen", "Failed to load puzzles", e)
            puzzleData = emptyList()
        }
    }
    val sortedPuzzles = puzzleData?.sortedByDescending { it.third }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Hello, $userName!",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(16.dp))
            when {
                puzzleData == null -> {
                    Text(
                        text = "Loading puzzles...",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                sortedPuzzles.isNullOrEmpty() -> {
                    Text(
                        text = "No puzzles found.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                else -> {
                    sortedPuzzles.forEach { (id, imageUrl, timestamp) ->
                        PuzzleCard(
                            puzzleId = id,
                            timestamp = timestamp,
                            imageUrl = imageUrl,
                            onDelete = {
                                puzzleData = puzzleData?.filterNot { it.first == id }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PuzzleCard(
    puzzleId: String,
    timestamp: Long,
    imageUrl: String,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val shape = RoundedCornerShape(4.dp)
    var showDialog by remember { mutableStateOf(false) }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Delete Puzzle") },
            text = { Text("Are you sure you want to delete this puzzle?") },
            confirmButton = {
                TextButton(onClick = {
                    FirebaseFirestore.getInstance().collection("puzzles").document(puzzleId)
                        .delete()
                        .addOnSuccessListener {
                            Toast.makeText(context, "Puzzle deleted", Toast.LENGTH_SHORT).show()
                            onDelete()
                        }
                    showDialog = false
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.primary, shape)
            .clip(shape),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(4.dp),
        shape = shape
    ) {
        Box {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Puzzle ID:", style = MaterialTheme.typography.bodySmall)
                        Text(puzzleId, style = MaterialTheme.typography.bodyLarge)
                    }
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(puzzleId))
                        Toast.makeText(context, "Puzzle ID copied", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy ID")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Created at: ${
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(
                            Date(timestamp)
                        )
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            IconButton(
                onClick = { showDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun PicturePuzzleScreen(
    modifier: Modifier = Modifier,
    selectedTab: Tab,
    usernameState: String,
    userId: String
) {
    val context = LocalContext.current
    var puzzlePieces by remember { mutableStateOf<List<Bitmap>?>(null) }
    var selectedIndices by remember { mutableStateOf<List<Int>>(emptyList()) }
    var originalImage by remember { mutableStateOf<Bitmap?>(null) }
    var showOriginal by remember { mutableStateOf(false) }
    var gridSize by remember { mutableStateOf(7f) }
    var loadedGridSize by remember { mutableStateOf<Int?>(null) }
    val intGridSize = gridSize.toInt()
    val realAnswer = remember { mutableStateListOf<Int>() }
    var realAns by remember { mutableStateOf<List<Int>>(emptyList()) }  // This holds the correct answer from Firestore
    var puzzleDocId by remember { mutableStateOf<String?>(null) }
    var puzzleCreatedBy by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val clipboardManager: ClipboardManager = LocalClipboardManager.current
    val cloudinary = remember {
        Cloudinary(ObjectUtils.asMap(
            "cloud_name", "dbodiyy03",
            "api_key", "623655253813631",
            "api_secret", "wgpIEQDPt-1290wIeJd-Z41k0o"
        ))
    }
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            originalBitmap?.let {
                val maxWidth = 700
                val scale = maxWidth.toFloat() / it.width
                val resized = Bitmap.createScaledBitmap(it, maxWidth, (it.height * scale).toInt(), true)
                originalImage = resized
                val originalPieces = splitImageIntoPieces(resized, intGridSize)
                val shuffledPieces = originalPieces.toMutableList()
                shuffledPieces.shuffle(Random(System.currentTimeMillis()))
                puzzlePieces = shuffledPieces
                showOriginal = false
                realAnswer.clear()
                realAnswer.addAll(shuffledPieces.map { piece -> originalPieces.indexOf(piece) })
                val stream = ByteArrayOutputStream()
                resized.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                val imageData = stream.toByteArray()
                val executor = Executors.newSingleThreadExecutor()
                executor.execute {
                    try {
                        val uploadResult = cloudinary.uploader().upload(imageData, ObjectUtils.emptyMap())
                        val imageUrl = uploadResult["secure_url"] as String
                        val timestamp = System.currentTimeMillis()
                        val db = FirebaseFirestore.getInstance()
                        val puzzleData = hashMapOf(
                            "timestamp" to timestamp,
                            "image" to imageUrl,
                            "real_ans" to realAnswer.toList(),
                            "gridSize" to intGridSize,
                            "created_by" to usernameState
                        )
                        db.collection("puzzles")
                            .add(puzzleData)
                            .addOnSuccessListener { documentRef ->
                                puzzleDocId = documentRef.id
                                Log.d("Firestore", "Puzzle created with image: $imageUrl")
                                db.collection("users")
                                    .document(userId)
                                    .update("puzzlesOwned", FieldValue.increment(1))
                                    .addOnSuccessListener {
                                        Log.d("Firestore", "puzzlesOwned incremented")
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("Firestore", "Error incrementing puzzlesOwned", e)
                                    }
                            }
                            .addOnFailureListener { e ->
                                Log.w("Firestore", "Error storing puzzle", e)
                            }
                    } catch (e: Exception) {
                        Log.e("Cloudinary", "Upload failed", e)
                    }
                }
            }
        }
    }
    Box(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(bottom = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Make Puzzle of Your Photo", fontSize = 20.sp)
            Spacer(modifier = Modifier.height(12.dp))
            if (puzzlePieces == null && loadedGridSize == null && selectedTab == Tab.HOME) {
                Text("(Grid Size: ${intGridSize} x ${intGridSize})", fontSize = 14.sp)
                Slider(
                    value = gridSize,
                    onValueChange = { gridSize = it },
                    valueRange = 3f..25f,
                    steps = 22,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(3.dp))
            }
            if (selectedTab == Tab.HOME) {
                Button(
                    onClick = { imagePickerLauncher.launch("image/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Pick Image")
                }
            }
            if (selectedTab == Tab.MY_PUZZLES) {
                var puzzleIdInput by remember { mutableStateOf("") }

                OutlinedTextField(
                    value = puzzleIdInput,
                    onValueChange = { puzzleIdInput = it },
                    label = { Text("Enter Puzzle ID") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )

                Button(
                    onClick = {
                        val db = FirebaseFirestore.getInstance()
                        db.collection("puzzles").document(puzzleIdInput.trim())
                            .get()
                            .addOnSuccessListener { document ->
                                if (document.exists()) {
                                    val imageUrl = document.getString("image")
                                    val realAnsList = document["real_ans"] as? List<Long>
                                    realAns = realAnsList?.map { it.toInt() } ?: emptyList()  // <- FIX: assign to outer state
                                    Log.d("MyAppDebug", "realAns (state) is: $realAns")
                                    val dbGridSize = (document.getLong("gridSize") ?: 0L).toInt()
                                    puzzleCreatedBy = document.getString("created_by")
                                    loadedGridSize = dbGridSize

                                    if (imageUrl != null && realAns.isNotEmpty()) {
                                        val executor = Executors.newSingleThreadExecutor()
                                        executor.execute {
                                            try {
                                                val url = java.net.URL(imageUrl)
                                                val bitmap = BitmapFactory.decodeStream(url.openConnection().getInputStream())
                                                val resized = Bitmap.createScaledBitmap(bitmap, 700,
                                                    (bitmap.height.toFloat() / bitmap.width * 700).toInt(), true)
                                                originalImage = resized
                                                val pieces = splitImageIntoPieces(resized, dbGridSize)
                                                val arrangedPieces = realAns.map { pieces[it] }
                                                puzzlePieces = arrangedPieces
                                                realAnswer.clear()
                                                realAnswer.addAll(realAns)
                                                puzzleDocId = document.id
                                                showOriginal = false
                                            } catch (e: Exception) {
                                                Log.e("PuzzleLoad", "Error loading puzzle", e)
                                            }
                                        }
                                    }
                                } else {
                                    Log.w("PuzzleLoad", "Puzzle not found")
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("PuzzleLoad", "Error fetching puzzle", e)
                            }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Load Puzzle")
                }
            }
            if (selectedTab == Tab.SETTINGS) {
                UserGreetingScreen(userName = usernameState)
            }
            if (puzzleDocId != null && selectedTab != Tab.SETTINGS) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp)
                        .background(Color.Black, RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFF90CAF9), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Puzzle ID: $puzzleDocId",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(onClick = {
                            clipboardManager.setText(AnnotatedString(puzzleDocId!!))
                            Toast.makeText(context, "Puzzle ID copied to clipboard!", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy Puzzle ID")
                        }
                    }
                }
                puzzleCreatedBy?.let { creator ->
                    Text(
                        text = "Created by: ${if (creator == usernameState) "You" else creator}",
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
            if (puzzlePieces != null && selectedTab != Tab.SETTINGS) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        puzzlePieces = null
                        selectedIndices = emptyList()
                        originalImage = null
                        showOriginal = false
                        realAnswer.clear()
                        realAns = emptyList()
                        puzzleDocId = null
                        loadedGridSize = null
                    }) {
                        Text("Clear")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if(selectedTab != Tab.SETTINGS){
                puzzlePieces?.let { pieces ->
                    val usedGridSize = loadedGridSize ?: intGridSize
                    val imageWidth = 350.dp
                    val pieceHeight = if (originalImage != null)
                        (imageWidth.value * (originalImage!!.height.toFloat() / originalImage!!.width)).dp
                    else imageWidth

                    val blockWidth = imageWidth / usedGridSize
                    val blockHeight = pieceHeight / usedGridSize
                    val spacing = if (usedGridSize > 10) 0.dp else 1.dp

                    Box(modifier = Modifier.width(imageWidth).height(pieceHeight).background(Color.LightGray)) {
                        Column {
                            for (row in 0 until usedGridSize) {
                                Row {
                                    for (col in 0 until usedGridSize) {
                                        val index = row * usedGridSize + col
                                        val piece = pieces[index]
                                        Image(
                                            bitmap = piece.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .size(blockWidth, blockHeight)
                                                .padding(spacing)
                                                .border(
                                                    2.dp,
                                                    if (selectedIndices.contains(index)) Color.Red else Color.Black
                                                )
                                                .clickable {
                                                    if (selectedIndices.contains(index)) {
                                                        selectedIndices = selectedIndices - index
                                                    } else {
                                                        selectedIndices = (selectedIndices + index).take(2)
                                                        if (selectedIndices.size == 2) {
                                                            val (i1, i2) = selectedIndices
                                                            val newPieces = pieces.toMutableList()
                                                            val temp = newPieces[i1]
                                                            newPieces[i1] = newPieces[i2]
                                                            newPieces[i2] = temp

                                                            val tempAns = realAnswer[i1]
                                                            realAnswer[i1] = realAnswer[i2]
                                                            realAnswer[i2] = tempAns

                                                            puzzlePieces = newPieces
                                                            selectedIndices = emptyList()
                                                        }
                                                    }
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } ?: Text("No puzzle loaded yet.")
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val isCorrect = realAnswer.toList().indices.all { i -> realAnswer[i] == i }
                        if (isCorrect) {
                            Toast.makeText(context, "Correct! Puzzle solved.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Incorrect! Keep trying.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Submit")
                }
            }

            if (puzzlePieces != null && selectedTab != Tab.SETTINGS) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showOriginal = !showOriginal },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(2.dp)
                ) {
                    Text(if (showOriginal) "Hide Image" else "Reveal Image")
                }
            }
            if (showOriginal && originalImage != null && selectedTab != Tab.SETTINGS) {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Original Image")
                val screenWidth = LocalConfiguration.current.screenWidthDp.dp
                val maxWidth = screenWidth - 32.dp
                Image(
                    bitmap = originalImage!!.asImageBitmap(),
                    contentDescription = "Original Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxWidth)
                        .padding(4.dp),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }
}


fun splitImageIntoPieces(original: Bitmap, gridSize: Int): List<Bitmap> {
    val pieceWidth = original.width / gridSize
    val pieceHeight = original.height / gridSize
    val pieces = mutableListOf<Bitmap>()
    for (row in 0 until gridSize) {
        for (col in 0 until gridSize) {
            val piece = Bitmap.createBitmap(
                original,
                col * pieceWidth,
                row * pieceHeight,
                pieceWidth,
                pieceHeight
            )
            pieces.add(piece)
        }
    }
    return pieces
}

@Composable
fun UsernameScreen(
    usernameState: String,
    onUsernameChange: (String) -> Unit,
    isUsernameAvailable: Boolean,
    onCheckUsername: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF181818)) // Background color
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TypewriterAnimatedText("Picture Puzzle") // 🧩 animation
        Spacer(modifier = Modifier.height(32.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Enter Username",
                fontSize = 18.sp,
                color = Color.White,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
            BasicTextField(
                value = usernameState,
                onValueChange = onUsernameChange,
                textStyle = TextStyle(fontSize = 16.sp, color = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp) // Reduced height
                    .background(Color.Gray.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        if (!isUsernameAvailable) {
            Text(
                "Username is already taken, please try another.",
                color = Color.Red,
                fontSize = 14.sp
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onCheckUsername,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text("Check Username")
        }
    }
}


@Composable
fun TypewriterAnimatedText(text: String) {
    var displayText by remember { mutableStateOf("") }
    var forward by remember { mutableStateOf(true) }
    LaunchedEffect(text) {
        while (true) {
            delay(100L)
            displayText = if (forward) {
                if (displayText.length < text.length) {
                    text.substring(0, displayText.length + 1)
                } else {
                    delay(1000L)
                    forward = false
                    displayText
                }
            } else {
                if (displayText.isNotEmpty()) {
                    text.substring(0, displayText.length - 1)
                } else {
                    delay(500L)
                    forward = true
                    ""
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "🧩",
            fontSize = 32.sp,
            color = Color.White
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = displayText,
            fontSize = 30.sp,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun UserPuzzleCard(puzzleId: String, createdAt: Long, imageUrl: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Puzzle ID: $puzzleId")
                Text(text = "Created: ${Date(createdAt)}")
            }
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun SettingsScreen(userName: String) {
    val db = FirebaseFirestore.getInstance()
    var userPuzzles by remember { mutableStateOf<List<DocumentSnapshot>>(emptyList()) }
    LaunchedEffect(userName) {
        try {
            val result = db.collection("puzzles")
                .whereEqualTo("created_by", userName)
                .get()
                .await()
            userPuzzles = result.documents
        } catch (e: Exception) {
            Log.e("SettingsScreen", "Failed to load puzzles", e)
        }
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        items(userPuzzles) { document ->
            val id = document.id
            val createdAt = document.getLong("timestamp") ?: 0L
            val imageUrl = document.getString("image") ?: ""
            UserPuzzleCard(puzzleId = id, createdAt = createdAt, imageUrl = imageUrl)
        }
    }
}
