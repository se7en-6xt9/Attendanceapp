package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.theme.MyApplicationTheme
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDropDown

// --- Modifiers for Glassy Look ---
val AppGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF0F2027), // Dark slate
        Color(0xFF203A43), // Teal slate
        Color(0xFF2C5364)  // Dark blue slate
    )
)

fun Modifier.glassyEffect(cornerRadius: androidx.compose.ui.unit.Dp = 32.dp) = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(Color.White.copy(alpha = 0.05f))
    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(cornerRadius))

fun Modifier.solidDialogEffect(cornerRadius: androidx.compose.ui.unit.Dp = 32.dp) = this
    .clip(RoundedCornerShape(cornerRadius))
    .background(Color(0xFF1E2836)) 
    .border(1.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(cornerRadius))

fun Modifier.pressClickable(onClick: () -> Unit) = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f, label = "scale")
    
    this
        .scale(scale)
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = onClick
        )
}

@Composable
fun CourseChip(text: String, isClickable: Boolean = false, maxWidth: androidx.compose.ui.unit.Dp = 140.dp) {
    var showFullTextDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .let { if (isClickable) it.pressClickable { showFullTextDialog = true } else it }
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .widthIn(max = maxWidth)
    ) {
        Text(
            text = text, 
            style = MaterialTheme.typography.bodyLarge, 
            color = Color.White, 
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }

    if (showFullTextDialog) {
        Dialog(onDismissRequest = { showFullTextDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .solidDialogEffect()
                    .padding(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = text, 
                        color = Color.White, 
                        style = MaterialTheme.typography.titleMedium, 
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { showFullTextDialog = false }) { 
                        Icon(Icons.Default.Close, "Close", tint = Color.White) 
                    }
                }
            }
        }
    }
}

// --- Models & ViewModel ---
data class Course(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val branch: String,
    val subject: String,
    val semester: String? = null,
    val year: String = "2024",
    val extraFields: List<String> = emptyList(),
    val visibleExtraFields: Set<Int> = emptySet()
)

class AttendanceViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).attendanceDao()

    private val sharedPrefs = application.getSharedPreferences("attendance_prefs", android.content.Context.MODE_PRIVATE)
    
    val selectedYear = kotlinx.coroutines.flow.MutableStateFlow(sharedPrefs.getString("selected_year", java.time.Year.now().value.toString()) ?: java.time.Year.now().value.toString())
    
    private val _customYears = kotlinx.coroutines.flow.MutableStateFlow(
        sharedPrefs.getStringSet("custom_years", setOf(java.time.Year.now().value.toString()))?.toSet() ?: setOf(java.time.Year.now().value.toString())
    )
    val customYears: StateFlow<Set<String>> = _customYears

    fun addCustomYear(year: String) {
        val current = _customYears.value.toMutableSet()
        current.add(year)
        _customYears.value = current
        sharedPrefs.edit().putStringSet("custom_years", current).apply()
        setSelectedYear(year)
    }

    fun setSelectedYear(year: String) {
        selectedYear.value = year
        sharedPrefs.edit().putString("selected_year", year).apply()
    }
    
    val courses: StateFlow<List<Course>> = dao.getAllCourses()
        .map { list ->
            list.map { entity ->
                Course(
                    id = entity.id,
                    name = entity.name,
                    branch = entity.branch,
                    subject = entity.subject,
                    semester = entity.semester,
                    year = entity.year,
                    extraFields = if (entity.extraFieldsStr.isBlank()) emptyList() else entity.extraFieldsStr.split("||"),
                    visibleExtraFields = if (entity.visibleExtraFieldsStr.isBlank()) emptySet() else entity.visibleExtraFieldsStr.split(",").mapNotNull { it.toIntOrNull() }.toSet()
                )
            }
        }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    fun addCourse(name: String, branch: String, subject: String, semester: String?, year: String, extraFields: List<String>) {
        viewModelScope.launch {
            val newCourse = CourseEntity(
                name = name,
                branch = branch,
                subject = subject,
                semester = semester,
                year = year,
                extraFieldsStr = extraFields.joinToString("||"),
                visibleExtraFieldsStr = ""
            )
            dao.insertCourse(newCourse)
        }
    }

    fun toggleExtraFieldVisibility(courseId: String, fieldIndex: Int) {
        viewModelScope.launch {
            val courseEntity = dao.getCourseById(courseId)
            if (courseEntity != null) {
                val currentVisibleStr = courseEntity.visibleExtraFieldsStr
                val currentVisible = if (currentVisibleStr.isBlank()) mutableSetOf<Int>() else currentVisibleStr.split(",").mapNotNull { it.toIntOrNull() }.toMutableSet()
                
                if (currentVisible.contains(fieldIndex)) {
                    currentVisible.remove(fieldIndex)
                } else {
                    currentVisible.add(fieldIndex)
                }
                val newVisibleStr = currentVisible.joinToString(",")
                dao.insertCourse(courseEntity.copy(visibleExtraFieldsStr = newVisibleStr))
            }
        }
    }

    fun getStudentsForCourse(courseId: String): StateFlow<List<StudentEntity>> {
        return dao.getStudents(courseId)
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun getAttendanceForCourse(courseId: String): StateFlow<List<AttendanceEntity>> {
        return dao.getAttendanceForCourse(courseId)
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun addStudent(courseId: String, name: String, rollNo: String) {
        viewModelScope.launch {
            dao.insertStudent(StudentEntity(courseId = courseId, name = name, rollNo = rollNo))
        }
    }

    fun updateStudent(student: StudentEntity) {
        viewModelScope.launch {
            dao.updateStudent(student)
        }
    }

    fun deleteStudent(student: StudentEntity) {
        viewModelScope.launch {
            dao.deleteStudent(student)
        }
    }

    fun setAttendance(studentId: String, dateStr: String, status: String) {
        viewModelScope.launch {
            dao.insertAttendance(AttendanceEntity(studentId, dateStr, status))
        }
    }
    fun getCourseFlow(courseId: String): StateFlow<CourseEntity?> {
        return dao.getCourseByIdFlow(courseId)
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), null)
    }

    fun toggleRollNoVisibility(courseId: String) {
        viewModelScope.launch {
            val course = dao.getCourseById(courseId)
            if (course != null) {
                dao.insertCourse(course.copy(isRollNoHidden = !course.isRollNoHidden))
            }
        }
    }

    fun getCourseFields(courseId: String): StateFlow<List<CourseFieldEntity>> {
        return dao.getCourseFields(courseId)
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun addCourseField(courseId: String, name: String, type: String) {
        viewModelScope.launch {
            val currentFields = dao.getCourseFields(courseId).first()
            val nextOrder = (currentFields.maxOfOrNull { it.displayOrder } ?: -1) + 1
            dao.insertCourseField(CourseFieldEntity(courseId = courseId, name = name, type = type, displayOrder = nextOrder))
        }
    }

    fun toggleCourseFieldVisibility(field: CourseFieldEntity) {
        viewModelScope.launch {
            dao.updateCourseField(field.copy(isHidden = !field.isHidden))
        }
    }

    fun getStudentFieldValuesForCourse(courseId: String): StateFlow<List<StudentFieldValueEntity>> {
        return dao.getStudentFieldValuesForCourse(courseId)
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun setStudentFieldValue(studentId: String, fieldId: String, value: String) {
        viewModelScope.launch {
            dao.insertStudentFieldValue(StudentFieldValueEntity(studentId, fieldId, value))
        }
    }
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AttendanceApp()
            }
        }
    }
}

@Composable
fun AttendanceApp() {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    val savedRole = sharedPreferences.getString("user_role", null)
    
    val startDestination = when (savedRole) {
        "teacher" -> "teacher_home"
        "student" -> "student_home" // Just in case, though not implemented
        else -> "login"
    }

    val navController = rememberNavController()
    val viewModel: AttendanceViewModel = viewModel()

    Box(modifier = Modifier.fillMaxSize().background(AppGradient)) {
        NavHost(
            navController = navController, 
            startDestination = startDestination,
            enterTransition = { androidx.compose.animation.slideInHorizontally(initialOffsetX = { it }) + androidx.compose.animation.fadeIn() },
            exitTransition = { androidx.compose.animation.slideOutHorizontally(targetOffsetX = { -it }) + androidx.compose.animation.fadeOut() },
            popEnterTransition = { androidx.compose.animation.slideInHorizontally(initialOffsetX = { -it }) + androidx.compose.animation.fadeIn() },
            popExitTransition = { androidx.compose.animation.slideOutHorizontally(targetOffsetX = { it }) + androidx.compose.animation.fadeOut() }
        ) {
            composable("login") { LoginScreen(navController) }
            composable("role_selection") { RoleSelectionScreen(navController, sharedPreferences) }
            composable("teacher_home") { TeacherHomeScreen(navController, viewModel) }
            composable("student_home") { 
                // Placeholder for future
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Student Home - Coming Soon") }
            }
            composable("create_course") { CreateCourseScreen(navController, viewModel) }
            composable("course_detail/{courseId}") { backStackEntry ->
                val courseId = backStackEntry.arguments?.getString("courseId")
                CourseDetailScreen(navController, courseId)
            }
            composable("enter_student_data/{courseId}") { backStackEntry ->
                val courseId = backStackEntry.arguments?.getString("courseId")
                EnterStudentDataScreen(navController, courseId, viewModel)
            }
            composable("take_attendance/{courseId}") { backStackEntry ->
                val courseId = backStackEntry.arguments?.getString("courseId")
                TakeAttendanceScreen(navController, courseId, viewModel)
            }
        }
    }
}

// --- Screens ---

@Composable
fun LoginScreen(navController: NavController) {
    Scaffold(containerColor = Color.Transparent) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Attendance App",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 48.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .glassyEffect()
                    .pressClickable { navController.navigate("role_selection") },
                contentAlignment = Alignment.Center
            ) {
                Text("Login with Google", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleSelectionScreen(navController: NavController, sharedPreferences: android.content.SharedPreferences) {
    val context = LocalContext.current
    Scaffold(containerColor = Color.Transparent) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Select Your Role",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .glassyEffect()
                    .pressClickable {
                        sharedPreferences.edit().putString("user_role", "teacher").apply()
                        navController.navigate("teacher_home") {
                            popUpTo("login") { inclusive = true }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("Teacher", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .glassyEffect()
                    .pressClickable {
                        Toast.makeText(context, "Student mode is coming soon", Toast.LENGTH_SHORT).show()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("Student", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherHomeScreen(navController: NavController, viewModel: AttendanceViewModel) {
    val courses by viewModel.courses.collectAsState()
    val selectedYear by viewModel.selectedYear.collectAsState()
    val customYears by viewModel.customYears.collectAsState()
    
    val availableYears = remember(courses, customYears) {
        val years = courses.map { it.year.trim() }.toMutableSet()
        years.addAll(customYears.map { it.trim() })
        years.filter { it.isNotBlank() }.distinct().sortedDescending()
    }
    
    val filteredCourses = remember(courses, selectedYear) {
        courses.filter { it.year == selectedYear }
    }

    var showYearDropdown by remember { mutableStateOf(false) }
    var showNewYearDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { 
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth().padding(end = 16.dp)
                    ) {
                        Text("My Courses")
                        
                        // Year Selector
                        Box {
                            Box(
                                modifier = Modifier
                                    .glassyEffect(12.dp)
                                    .pressClickable { showYearDropdown = true }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(selectedYear, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Year", tint = Color.White)
                                }
                            }
                            
                            DropdownMenu(
                                expanded = showYearDropdown,
                                onDismissRequest = { showYearDropdown = false },
                                modifier = Modifier.background(Color(0xFF2C3E50))
                            ) {
                                availableYears.forEach { year ->
                                    DropdownMenuItem(
                                        text = { Text(year, color = Color.White) },
                                        onClick = {
                                            viewModel.setSelectedYear(year)
                                            showYearDropdown = false
                                        }
                                    )
                                }
                                androidx.compose.material3.HorizontalDivider(color = Color.White.copy(alpha=0.1f))
                                DropdownMenuItem(
                                    text = { Text("+ New Year", color = Color(0xFF69F0AE), fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        showYearDropdown = false
                                        showNewYearDialog = true
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            if (filteredCourses.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .glassyEffect()
                        .pressClickable { navController.navigate("create_course") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Course", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
    ) { innerPadding ->
        if (showNewYearDialog) {
            var newYearText by remember { mutableStateOf("") }
            Dialog(onDismissRequest = { showNewYearDialog = false }) {
                Box(modifier = Modifier.fillMaxWidth().solidDialogEffect().padding(24.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Create New Year", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        OutlinedTextField(
                            value = newYearText, 
                            onValueChange = { newYearText = it },
                            label = { Text("Year (e.g. 2026)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedLabelColor = Color.White,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                                cursorColor = Color.White
                            )
                        )
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { showNewYearDialog = false }) { Text("Cancel", color = Color.White) }
                            Button(onClick = { 
                                if (newYearText.isNotBlank()) {
                                    viewModel.addCustomYear(newYearText)
                                }
                                showNewYearDialog = false 
                            }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) { 
                                Text("Create") 
                            }
                        }
                    }
                }
            }
        }

        if (filteredCourses.isEmpty()) {
             Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Welcome to $selectedYear!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = "Create your first course in this year to get started.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 32.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .glassyEffect()
                        .pressClickable { navController.navigate("create_course") },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Create Course", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(filteredCourses) { course ->
                    CourseCard(
                        course = course, 
                        onClick = {
                            navController.navigate("course_detail/${course.id}")
                        },
                        onToggleExtraField = { index ->
                            viewModel.toggleExtraFieldVisibility(course.id, index)
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CourseCard(course: Course, onClick: () -> Unit, onToggleExtraField: (Int) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassyEffect()
            .pressClickable { onClick() }
            .padding(24.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f, fill=false)
                )

                IconButton(onClick = { showDialog = true }, modifier = Modifier.padding(start = 4.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp), 
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CourseChip(text = course.branch, isClickable = true)
                CourseChip(text = course.subject, isClickable = true)
                course.semester?.let {
                    CourseChip(text = it, isClickable = true)
                }
                course.visibleExtraFields.sorted().forEach { index ->
                    course.extraFields.getOrNull(index)?.let { field ->
                        CourseChip(text = field, isClickable = true)
                    }
                }
            }
        }
    }

    if (showDialog) {
        Dialog(
            onDismissRequest = { showDialog = false },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .solidDialogEffect()
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Extra Info",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        IconButton(onClick = { showDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    if (course.extraFields.isEmpty()) {
                        Text("No extra fields for this course.", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    } else {
                        course.extraFields.forEachIndexed { index, field ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .glassyEffect(8.dp)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}. $field", 
                                    style = MaterialTheme.typography.bodyLarge, 
                                    color = Color.White,
                                    modifier = Modifier.weight(1f)
                                )
                                Checkbox(
                                    checked = course.visibleExtraFields.contains(index),
                                    onCheckedChange = { onToggleExtraField(index) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color.White,
                                        checkmarkColor = Color.White,
                                        uncheckedColor = Color.White.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCourseScreen(navController: NavController, viewModel: AttendanceViewModel) {
    val selectedYear by viewModel.selectedYear.collectAsState()
    var courseName by remember { mutableStateOf("") }
    var branch by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var semesterLabel by remember { mutableStateOf<String?>(null) }
    var showSemesterDialog by remember { mutableStateOf(false) }
    val extraFields = remember { mutableStateListOf<String>() }
    val context = LocalContext.current

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("New Course") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(vertical = 24.dp)
        ) {
            item {
                OutlinedTextField(
                    value = courseName,
                    onValueChange = { courseName = it },
                    label = { Text("Course Name") },
                    modifier = Modifier.fillMaxWidth().glassyEffect(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color.White.copy(alpha = 0.8f),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.8f)
                    ),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text("Branch") },
                    modifier = Modifier.fillMaxWidth().glassyEffect(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color.White.copy(alpha = 0.8f),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.8f)
                    ),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject") },
                    modifier = Modifier.fillMaxWidth().glassyEffect(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color.White.copy(alpha = 0.8f),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.8f)
                    ),
                    singleLine = true
                )
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassyEffect(16.dp)
                        .pressClickable { showSemesterDialog = true }
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Text(
                        text = semesterLabel ?: "Select Semester (Optional)",
                        color = if (semesterLabel == null) Color.White.copy(alpha = 0.8f) else Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            items(extraFields.size) { index ->
                OutlinedTextField(
                    value = extraFields[index],
                    onValueChange = { newValue -> extraFields[index] = newValue },
                    label = { Text("Extra Field ${index + 1}") },
                    modifier = Modifier.fillMaxWidth().glassyEffect(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color.White.copy(alpha = 0.8f),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.8f)
                    ),
                    singleLine = true
                )
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .glassyEffect(16.dp)
                        .pressClickable { extraFields.add("") },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = "Add Item", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add extra field...", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .glassyEffect(24.dp)
                        .pressClickable {
                            if (courseName.isNotBlank() && branch.isNotBlank() && subject.isNotBlank()) {
                                viewModel.addCourse(courseName, branch, subject, semesterLabel, selectedYear, extraFields.filter { it.isNotBlank() }.toList())
                                navController.popBackStack()
                            } else {
                                Toast.makeText(context, "Please fill main fields", Toast.LENGTH_SHORT).show()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Create Course", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }

    if (showSemesterDialog) {
        Dialog(
            onDismissRequest = { showSemesterDialog = false },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true)
        ) {
            Box(modifier = Modifier.fillMaxWidth().solidDialogEffect().padding(24.dp)) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Select Semester", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                        IconButton(onClick = { showSemesterDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                        items((1..8).toList()) { sem ->
                            val year = (sem + 1) / 2
                            val suffix = when (year) {
                                1 -> "st"
                                2 -> "nd"
                                3 -> "rd"
                                else -> "th"
                            }
                            val label = "Sem $sem (${year}$suffix Yr)"
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .glassyEffect(8.dp)
                                    .pressClickable {
                                        semesterLabel = label
                                        showSemesterDialog = false
                                    }
                                    .padding(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(navController: NavController, courseId: String?) {
    val context = LocalContext.current
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Course Management") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .glassyEffect()
                    .pressClickable {
                        courseId?.let { navController.navigate("enter_student_data/$it") }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("Enter Student Data", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .glassyEffect()
                    .pressClickable {
                        courseId?.let { navController.navigate("take_attendance/$it") }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("Take Attendance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnterStudentDataScreen(navController: NavController, courseId: String?, viewModel: AttendanceViewModel) {
    if (courseId == null) return
    val students by remember(courseId) { viewModel.getStudentsForCourse(courseId) }.collectAsState()
    val attendances by remember(courseId) { viewModel.getAttendanceForCourse(courseId) }.collectAsState()

    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    
    var showAddStudentDialog by remember { mutableStateOf(false) }
    var studentName by remember { mutableStateOf("") }
    var studentRoll by remember { mutableStateOf("") }

    var studentToEdit by remember { mutableStateOf<StudentEntity?>(null) }
    var editName by remember { mutableStateOf("") }
    var editRoll by remember { mutableStateOf("") }

    var showAddFieldDialog by remember { mutableStateOf(false) }

    val daysRange = -365..0
    val today = remember { LocalDate.now() }
    val realDates = remember {
        daysRange.map { offset ->
            today.plusDays(offset.toLong())
        }
    }
    val formatter = remember { DateTimeFormatter.ofPattern("MMM d") }

    LaunchedEffect(horizontalScrollState.maxValue) {
        if (horizontalScrollState.maxValue > 0) {
            horizontalScrollState.scrollTo(horizontalScrollState.maxValue)
        }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color.White,
        unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        cursorColor = Color.White,
        focusedLabelColor = Color.White,
        unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
    )

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Enter Student Data", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddStudentDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Student", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White.copy(alpha = 0.5f),
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Dashboard
            val course = remember(courseId) { viewModel.getCourseFlow(courseId) }.collectAsState().value
            val courseFields by remember(courseId) { viewModel.getCourseFields(courseId) }.collectAsState()
            val studentFieldValues by remember(courseId) { viewModel.getStudentFieldValuesForCourse(courseId) }.collectAsState()

            if (course != null) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp).glassyEffect(16.dp).padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(course.subject, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 24.sp)
                            Text("Total Students: ${students.size}", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        Button(
                            onClick = { showAddFieldDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.border(1.dp, Color.White, RoundedCornerShape(16.dp)),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Add Field", fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    val hiddenFields = courseFields.filter { it.isHidden }
                    val isRollNoHidden = course.isRollNoHidden
                    if (isRollNoHidden || hiddenFields.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color.White.copy(alpha=0.1f))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Hidden Columns (Tap to restore)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isRollNoHidden) {
                                AssistChip(
                                    onClick = { viewModel.toggleRollNoVisibility(courseId) },
                                    label = { Text("Roll No", fontWeight = FontWeight.Medium) },
                                    trailingIcon = { Icon(Icons.Default.Add, contentDescription = "Restore", modifier = Modifier.size(16.dp)) },
                                    colors = AssistChipDefaults.assistChipColors(labelColor = Color.White, trailingIconContentColor = Color.White)
                                )
                            }
                            hiddenFields.forEach { field ->
                                AssistChip(
                                    onClick = { viewModel.toggleCourseFieldVisibility(field) },
                                    label = { Text(field.name, fontWeight = FontWeight.Medium) },
                                    trailingIcon = { Icon(Icons.Default.Add, contentDescription = "Restore", modifier = Modifier.size(16.dp)) },
                                    colors = AssistChipDefaults.assistChipColors(labelColor = Color.White, trailingIconContentColor = Color.White)
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .glassyEffect(24.dp)
                    .padding(16.dp)
            ) {
            val visibleCourseFields = courseFields.filter { !it.isHidden }
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val leftMaxWidth = (configuration.screenWidthDp * 0.6f).dp

            Row(modifier = Modifier.fillMaxWidth().verticalScroll(verticalScrollState)) {
                // Left Fixed Section (Name, Roll No, Custom Fields)
                Column(
                    modifier = Modifier
                        .widthIn(max = leftMaxWidth)
                        .horizontalScroll(rememberScrollState())
                ) {
                    Row(modifier = Modifier.height(72.dp)) {
                        Box(modifier = Modifier.width(100.dp).fillMaxHeight().padding(4.dp).solidDialogEffect(8.dp), contentAlignment = Alignment.Center) { Text("Name", fontWeight = FontWeight.Bold, color = Color.White) }
                        if (course?.isRollNoHidden == false) {
                            Box(modifier = Modifier.width(100.dp).fillMaxHeight().padding(4.dp).solidDialogEffect(8.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(Icons.Default.Close, contentDescription = "Hide Roll No", modifier = Modifier.size(16.dp).clickable { viewModel.toggleRollNoVisibility(courseId) }, tint = Color.White)
                                    Text("Roll No", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                        visibleCourseFields.forEach { field ->
                            Box(modifier = Modifier.width(100.dp).fillMaxHeight().padding(4.dp).solidDialogEffect(8.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(Icons.Default.Close, contentDescription = "Hide", modifier = Modifier.size(16.dp).clickable { viewModel.toggleCourseFieldVisibility(field) }, tint = Color.White)
                                    Text(field.name, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    students.forEach { student ->
                        Row(modifier = Modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.width(100.dp).fillMaxHeight().padding(4.dp).pressClickable {
                                editName = student.name
                                editRoll = student.rollNo
                                studentToEdit = student
                            }.solidDialogEffect(8.dp), contentAlignment = Alignment.Center) {
                                Text(if (student.isHidden) "***" else student.name, color = Color.White.copy(alpha = if (student.isFrozen) 0.5f else 0.9f), maxLines = 1, textDecoration = if (student.isFrozen) androidx.compose.ui.text.style.TextDecoration.LineThrough else null, modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp))
                            }
                            if (course?.isRollNoHidden == false) {
                                Box(modifier = Modifier.width(100.dp).fillMaxHeight().padding(4.dp).pressClickable {
                                    editName = student.name
                                    editRoll = student.rollNo
                                    studentToEdit = student
                                }.solidDialogEffect(8.dp), contentAlignment = Alignment.Center) {
                                    Text(if (student.isHidden) "***" else student.rollNo, color = Color.White.copy(alpha = if (student.isFrozen) 0.5f else 0.7f), fontSize = 14.sp, maxLines = 1, modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp))
                                }
                            }
                            visibleCourseFields.forEach { field ->
                                val fieldValue = studentFieldValues.find { it.studentId == student.id && it.fieldId == field.id }?.value ?: ""
                                Box(
                                    modifier = Modifier.width(100.dp).fillMaxHeight().padding(4.dp).solidDialogEffect(8.dp).let {
                                        if (!student.isFrozen && !student.isHidden) {
                                            when(field.type) {
                                                "TOGGLE" -> it.pressClickable {
                                                    val nextVal = when(fieldValue) { "" -> "Y"; "Y" -> "N"; else -> "" }
                                                    viewModel.setStudentFieldValue(student.id, field.id, nextVal)
                                                }
                                                "CHECKBOX" -> it.pressClickable {
                                                    val nextVal = if(fieldValue == "true") "false" else "true"
                                                    viewModel.setStudentFieldValue(student.id, field.id, nextVal)
                                                }
                                                else -> it
                                            }
                                        } else it
                                    },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (student.isHidden) {
                                        Text("***", color = Color.White.copy(alpha=0.5f))
                                    } else {
                                        when(field.type) {
                                            "TOGGLE" -> Text(fieldValue, color = if(fieldValue == "Y") Color(0xFF2E7D32) else if(fieldValue == "N") Color(0xFFD32F2F) else Color.Transparent, fontWeight = FontWeight.Bold)
                                            "CHECKBOX" -> Checkbox(checked = fieldValue == "true", onCheckedChange = { 
                                                if (!student.isFrozen) { viewModel.setStudentFieldValue(student.id, field.id, if(it) "true" else "false") } 
                                            }, colors = CheckboxDefaults.colors(checkedColor = Color.White))
                                            "TEXT" -> {
                                                var localVal by remember(fieldValue) { mutableStateOf(fieldValue) }
                                                androidx.compose.foundation.text.BasicTextField(
                                                    value = localVal,
                                                    onValueChange = { 
                                                        localVal = it
                                                        viewModel.setStudentFieldValue(student.id, field.id, it)
                                                    },
                                                    enabled = !student.isFrozen,
                                                    textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center),
                                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                                                    singleLine = true
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right Scrollable Section (Calendar)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(horizontalScrollState)
                ) {
                    Row(modifier = Modifier.height(72.dp)) {
                        realDates.forEach { date ->
                            val isToday = date == today
                            Box(
                                modifier = Modifier
                                    .width(64.dp)
                                    .fillMaxHeight()
                                    .padding(4.dp)
                                    .solidDialogEffect(8.dp)
                                    .background(if (isToday) Color.White.copy(alpha=0.15f) else Color.Transparent, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(date.format(formatter), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp, maxLines = 1)
                                    Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    students.forEach { student ->
                        Row(modifier = Modifier.height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                            
                            realDates.forEach { date ->
                                val dateStr = date.toString()
                                val attendanceRecord = attendances.find { it.studentId == student.id && it.dateStr == dateStr }
                                val status = attendanceRecord?.status ?: ""
                                
                                Box(
                                    modifier = Modifier
                                        .width(64.dp)
                                        .fillMaxHeight()
                                        .padding(4.dp)
                                        .solidDialogEffect(8.dp)
                                        .let {
                                            if (!student.isFrozen && !student.isHidden) {
                                                it.pressClickable {
                                                    val nextStatus = when (status) {
                                                        "" -> "P"
                                                        "P" -> "A"
                                                        else -> ""
                                                    }
                                                    viewModel.setAttendance(student.id, dateStr, nextStatus)
                                                }
                                            } else it
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = status,
                                        color = when(status) {
                                            "P" -> Color(0xFF2E7D32)
                                            "A" -> Color(0xFFD32F2F)
                                            else -> Color.Transparent
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }

    if (showAddStudentDialog) {
        Dialog(onDismissRequest = { showAddStudentDialog = false }) {
            Box(modifier = Modifier.fillMaxWidth().solidDialogEffect().padding(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Add Student", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    OutlinedTextField(
                        value = studentName, onValueChange = { studentName = it }, label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, colors = textFieldColors
                    )
                    OutlinedTextField(
                        value = studentRoll, onValueChange = { studentRoll = it }, label = { Text("Roll No") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, colors = textFieldColors
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showAddStudentDialog = false }) { Text("Cancel", color = Color.White) }
                        Button(onClick = {
                            if (studentName.isNotBlank() && studentRoll.isNotBlank()) {
                                viewModel.addStudent(courseId, studentName, studentRoll)
                                studentName = ""
                                studentRoll = ""
                                showAddStudentDialog = false
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), shape = RoundedCornerShape(16.dp), modifier = Modifier.border(1.dp, Color.White, RoundedCornerShape(16.dp))) { Text("Add", color = Color.Black) }
                    }
                }
            }
        }
    }

    if (studentToEdit != null) {
        Dialog(onDismissRequest = { studentToEdit = null }) {
            Box(modifier = Modifier.fillMaxWidth().solidDialogEffect().padding(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Edit Student", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    OutlinedTextField(
                        value = editName, onValueChange = { editName = it }, label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, colors = textFieldColors
                    )
                    OutlinedTextField(
                        value = editRoll, onValueChange = { editRoll = it }, label = { Text("Roll No") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, colors = textFieldColors
                    )
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Freeze (Disable Editing)", color = Color.White)
                        Switch(checked = studentToEdit!!.isFrozen, onCheckedChange = { studentToEdit = studentToEdit!!.copy(isFrozen = it) })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Hide Details", color = Color.White)
                        Switch(checked = studentToEdit!!.isHidden, onCheckedChange = { studentToEdit = studentToEdit!!.copy(isHidden = it) })
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = {
                            viewModel.deleteStudent(studentToEdit!!)
                            studentToEdit = null
                        }) { Text("Delete", color = Color.Red) }
                        
                        Row {
                            TextButton(onClick = { studentToEdit = null }) { Text("Cancel", color = Color.White) }
                            Button(onClick = {
                                viewModel.updateStudent(studentToEdit!!.copy(name = editName, rollNo = editRoll))
                                studentToEdit = null
                            }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), shape = RoundedCornerShape(16.dp), modifier = Modifier.border(1.dp, Color.White, RoundedCornerShape(16.dp))) { Text("Save", color = Color.Black) }
                        }
                    }
                }
            }
        }
    }

    if (showAddFieldDialog) {
        var newFieldName by remember { mutableStateOf("") }
        var newFieldType by remember { mutableStateOf("TEXT") }
        var expanded by remember { mutableStateOf(false) }
        
        Dialog(onDismissRequest = { showAddFieldDialog = false }) {
            Box(modifier = Modifier.fillMaxWidth().solidDialogEffect().padding(24.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Add Field", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    OutlinedTextField(
                        value = newFieldName, onValueChange = { newFieldName = it }, label = { Text("Field Name") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, colors = textFieldColors
                    )
                    
                    Box {
                        OutlinedTextField(
                            value = when(newFieldType) {
                                "TEXT" -> "Alphanumeric Input"
                                "CHECKBOX" -> "Checkbox"
                                "TOGGLE" -> "Yes/No/Blank Toggle"
                                else -> newFieldType
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Field Type") },
                            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
                            colors = textFieldColors,
                            enabled = false,
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White)
                        )
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text("Alphanumeric Input") }, onClick = { newFieldType = "TEXT"; expanded = false })
                            DropdownMenuItem(text = { Text("Checkbox") }, onClick = { newFieldType = "CHECKBOX"; expanded = false })
                            DropdownMenuItem(text = { Text("Yes/No/Blank Toggle") }, onClick = { newFieldType = "TOGGLE"; expanded = false })
                        }
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showAddFieldDialog = false }) { Text("Cancel", color = Color.White) }
                        Button(onClick = {
                            if (newFieldName.isNotBlank()) {
                                viewModel.addCourseField(courseId, newFieldName, newFieldType)
                                showAddFieldDialog = false
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), shape = RoundedCornerShape(16.dp), modifier = Modifier.border(1.dp, Color.White, RoundedCornerShape(16.dp))) { Text("Create", color = Color.Black) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeAttendanceScreen(navController: NavController, courseId: String?, viewModel: AttendanceViewModel) {
    if (courseId == null) return
    val students by remember(courseId) { viewModel.getStudentsForCourse(courseId) }.collectAsState()
    val attendances by remember(courseId) { viewModel.getAttendanceForCourse(courseId) }.collectAsState()
    val studentFieldValues by remember(courseId) { viewModel.getStudentFieldValuesForCourse(courseId) }.collectAsState()
    val courseFields by remember(courseId) { viewModel.getCourseFields(courseId) }.collectAsState()

    val totalWorkingDays = remember(attendances) { attendances.map { it.dateStr }.distinct().size }
    
    val todayStr = remember { java.time.LocalDate.now().toString() }
    var forceRetake by remember { mutableStateOf(false) }
    
    val isAttendanceDoneForToday = remember(attendances, students) {
        if (students.isEmpty()) false
        else attendances.count { it.dateStr == todayStr } >= students.size
    }

    val continuousIndex = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    val screenWidthDp = configuration.screenWidthDp.toFloat()
    val screenWidthPx = screenWidthDp * density

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Take Attendance", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (students.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No students in this course.", color = Color.White, fontSize = 18.sp)
                }
            } else if (isAttendanceDoneForToday && !forceRetake) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Done", modifier = Modifier.size(100.dp), tint = Color(0xFF69F0AE))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Attendance Done!", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("You have already marked attendance for today.", color = Color.White.copy(alpha=0.7f), fontSize = 16.sp, textAlign = TextAlign.Center)
                    
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    Button(
                        onClick = { forceRetake = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Take Attendance Again", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = { navController.popBackStack() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Go Back", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            } else {
                // Top 60%: Stack of cards
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.6f)
                        .padding(bottom = 16.dp)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    val currentInt = kotlin.math.round(continuousIndex.value).toInt()
                                    val target = currentInt.coerceIn(0, students.size).toFloat()
                                    coroutineScope.launch {
                                        continuousIndex.animateTo(target, androidx.compose.animation.core.tween(300))
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    val dragFactor = dragAmount / (screenWidthPx * 0.5f)
                                    val oldInt = kotlin.math.round(continuousIndex.value).toInt()
                                    coroutineScope.launch {
                                        val newIndex = (continuousIndex.value + dragFactor).coerceIn(-0.5f, students.size + 0.5f)
                                        continuousIndex.snapTo(newIndex)
                                        val newInt = kotlin.math.round(newIndex).toInt()
                                        if (newInt != oldInt) {
                                            SoundHelper.playSwipeSound()
                                        }
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (kotlin.math.round(continuousIndex.value).toInt() >= students.size && students.isNotEmpty()) {
                        Text("Attendance Complete!", style = MaterialTheme.typography.headlineMedium, color = Color.White.copy(alpha=0.5f), fontWeight = FontWeight.Bold)
                    }

                    // Render cards
                    val currentFloat = continuousIndex.value
                    val indicesToRender = students.indices.sortedByDescending { kotlin.math.abs(it - currentFloat) }
                    
                    for (i in indicesToRender) {
                        val rel = i - currentFloat
                        if (kotlin.math.abs(rel) > 3.5f) continue
                        
                        val offsetX = if (rel == 0f) 0f else {
                            val maxShift = screenWidthDp * 0.70f 
                            if (rel < 0f) {
                                val bounded = rel.coerceAtLeast(-1f)
                                val stackOffset = if (rel < -1f) (kotlin.math.abs(rel) - 1f) * -35f else 0f
                                -bounded * maxShift + stackOffset
                            } else {
                                val bounded = rel.coerceAtMost(1f)
                                val stackOffset = if (rel > 1f) (rel - 1f) * 35f else 0f
                                -bounded * maxShift + stackOffset
                            }
                        }
                        
                        val scaleV = (1f - kotlin.math.abs(rel) * 0.12f).coerceAtLeast(0.5f)
                        val alphaV = (1f - kotlin.math.abs(rel) * 0.2f).coerceIn(0f, 1f)
                        val rotationZV = (rel * -5f).coerceIn(-15f, 15f)
                        val rotationYV = (rel * -15f).coerceIn(-40f, 40f)

                        StudentCard(
                            student = students[i],
                            totalWorkingDays = totalWorkingDays,
                            attendances = attendances,
                            fields = courseFields,
                            fieldValues = studentFieldValues,
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.75f)
                                .graphicsLayer {
                                    translationX = offsetX * density
                                    scaleX = scaleV
                                    scaleY = scaleV
                                    alpha = alphaV
                                    rotationZ = rotationZV
                                    rotationY = rotationYV
                                    cameraDistance = 16f * density
                                    shadowElevation = (16f * scaleV * alphaV)
                                    shape = RoundedCornerShape(24.dp)
                                    clip = true
                                }
                        )
                    }
                }

                // Bottom 40%: Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.4f)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Absent Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .glassyEffect(32.dp)
                            .border(2.dp, Color(0xFFFF5252).copy(alpha=0.3f), RoundedCornerShape(32.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFF2B2B2B), Color(0xFF1E1E1E))
                                ),
                                RoundedCornerShape(32.dp)
                            )
                            .pressClickable {
                                val currentInt = kotlin.math.round(continuousIndex.value).toInt()
                                if (currentInt < students.size) {
                                    SoundHelper.playAbsentSound()
                                    viewModel.setAttendance(students[currentInt].id, java.time.LocalDate.now().toString(), "A")
                                    coroutineScope.launch {
                                        val target = (currentInt + 1).coerceAtMost(students.size).toFloat()
                                        continuousIndex.animateTo(target, androidx.compose.animation.core.tween(400))
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Close, contentDescription = "Absent", modifier = Modifier.size(64.dp), tint = Color(0xFFFF5252))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("ABSENT", fontWeight = FontWeight.Black, fontSize = 24.sp, color = Color(0xFFFF5252))
                        }
                    }

                    // Present Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .glassyEffect(32.dp)
                            .border(2.dp, Color(0xFF69F0AE).copy(alpha=0.3f), RoundedCornerShape(32.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFF2B2B2B), Color(0xFF1E1E1E))
                                ),
                                RoundedCornerShape(32.dp)
                            )
                            .pressClickable {
                                val currentInt = kotlin.math.round(continuousIndex.value).toInt()
                                if (currentInt < students.size) {
                                    SoundHelper.playPresentSound()
                                    viewModel.setAttendance(students[currentInt].id, java.time.LocalDate.now().toString(), "P")
                                    coroutineScope.launch {
                                        val target = (currentInt + 1).coerceAtMost(students.size).toFloat()
                                        continuousIndex.animateTo(target, androidx.compose.animation.core.tween(400))
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Check, contentDescription = "Present", modifier = Modifier.size(64.dp), tint = Color(0xFF69F0AE))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("PRESENT", fontWeight = FontWeight.Black, fontSize = 24.sp, color = Color(0xFF69F0AE))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudentCard(
    student: StudentEntity,
    totalWorkingDays: Int,
    attendances: List<AttendanceEntity>,
    fields: List<CourseFieldEntity>,
    fieldValues: List<StudentFieldValueEntity>,
    modifier: Modifier = Modifier
) {
    val presentDays = attendances.count { it.studentId == student.id && it.status == "P" }
    val percentage = if (totalWorkingDays == 0) 100 else (presentDays * 100) / totalWorkingDays
    val indicatorColor = if (percentage >= 75) Color(0xFF69F0AE) else if (percentage >= 50) Color(0xFFFFD54F) else Color(0xFFFF5252)

    Box(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF2C3E50), Color(0xFF1A1A1D))
                )
            )
            .border(1.dp, Color.White.copy(alpha=0.15f), RoundedCornerShape(24.dp))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Big 3D box for Roll No center
            Box(
                modifier = Modifier
                    .wrapContentWidth()
                    .widthIn(min = 110.dp)
                    .height(110.dp)
                    .background(
                        Brush.linearGradient(listOf(Color(0xFF384358), Color(0xFF1E2430))),
                        RoundedCornerShape(24.dp)
                    )
                    .border(2.dp, Color.White.copy(alpha=0.2f), RoundedCornerShape(24.dp))
                    .shadow(16.dp, RoundedCornerShape(24.dp), ambientColor = Color.Black, spotColor = Color.Black)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = student.rollNo, 
                    fontSize = 32.sp, 
                    fontWeight = FontWeight.Black, 
                    color = Color.White,
                    maxLines = 1
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Name in 3D box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF23303F), RoundedCornerShape(12.dp))
                    .border(1.dp, Color.White.copy(alpha=0.1f), RoundedCornerShape(12.dp))
                    .shadow(4.dp, RoundedCornerShape(12.dp))
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = student.name, 
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.Bold, 
                    color = Color.White, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Attendance Indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(indicatorColor.copy(alpha=0.15f), RoundedCornerShape(16.dp))
                    .border(1.dp, indicatorColor.copy(alpha=0.3f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$percentage%", fontSize = 32.sp, fontWeight = FontWeight.Black, color = indicatorColor)
                    Text("Attendance ($presentDays/$totalWorkingDays days)", fontSize = 12.sp, color = indicatorColor.copy(alpha=0.8f), fontWeight = FontWeight.Bold)
                }
            }

            // Custom Fields
            val visibleFields = fields.filter { !it.isHidden }
            if (visibleFields.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleFields) { field ->
                        val value = fieldValues.find { it.studentId == student.id && it.fieldId == field.id }?.value ?: ""
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1E2836), RoundedCornerShape(8.dp))
                                .border(1.dp, Color.White.copy(alpha=0.05f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(field.name, color = Color(0xFFA0AAB5), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(if (value.isBlank()) "-" else value, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
