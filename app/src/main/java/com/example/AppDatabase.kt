package com.example

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

// Entities
@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val branch: String,
    val subject: String,
    val semester: String?,
    val year: String = "2024",
    val extraFieldsStr: String,
    val visibleExtraFieldsStr: String,
    val isRollNoHidden: Boolean = false
)

@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val courseId: String,
    val name: String,
    val rollNo: String,
    val isHidden: Boolean = false,
    val isFrozen: Boolean = false
)

@Entity(tableName = "attendance", primaryKeys = ["studentId", "dateStr"])
data class AttendanceEntity(
    val studentId: String,
    val dateStr: String, // yyyy-MM-dd
    val status: String // "P", "A"
)

@Entity(tableName = "course_fields")
data class CourseFieldEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val courseId: String,
    val name: String,
    val type: String, // "CHECKBOX", "TOGGLE", "TEXT"
    val isHidden: Boolean = false,
    val displayOrder: Int = 0
)

@Entity(tableName = "student_field_values", primaryKeys = ["studentId", "fieldId"])
data class StudentFieldValueEntity(
    val studentId: String,
    val fieldId: String,
    val value: String
)

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM courses")
    fun getAllCourses(): Flow<List<CourseEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: CourseEntity)

    @Query("SELECT * FROM courses WHERE id = :id")
    suspend fun getCourseById(id: String): CourseEntity?
    
    @Query("SELECT * FROM courses WHERE id = :id")
    fun getCourseByIdFlow(id: String): Flow<CourseEntity?>

    @Query("SELECT * FROM students WHERE courseId = :courseId")
    fun getStudents(courseId: String): Flow<List<StudentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: StudentEntity)
    
    @Update
    suspend fun updateStudent(student: StudentEntity)
    
    @Delete
    suspend fun deleteStudent(student: StudentEntity)

    @Query("SELECT * FROM attendance WHERE studentId IN (SELECT id FROM students WHERE courseId = :courseId)")
    fun getAttendanceForCourse(courseId: String): Flow<List<AttendanceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(attendance: AttendanceEntity)

    @Query("SELECT * FROM course_fields WHERE courseId = :courseId ORDER BY displayOrder ASC")
    fun getCourseFields(courseId: String): Flow<List<CourseFieldEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourseField(field: CourseFieldEntity)

    @Update
    suspend fun updateCourseField(field: CourseFieldEntity)
    
    @Query("SELECT * FROM student_field_values WHERE fieldId IN (SELECT id FROM course_fields WHERE courseId = :courseId)")
    fun getStudentFieldValuesForCourse(courseId: String): Flow<List<StudentFieldValueEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudentFieldValue(value: StudentFieldValueEntity)
}

@Database(entities = [CourseEntity::class, StudentEntity::class, AttendanceEntity::class, CourseFieldEntity::class, StudentFieldValueEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun attendanceDao(): AttendanceDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE courses ADD COLUMN isRollNoHidden INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS `course_fields` (`id` TEXT NOT NULL, `courseId` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `isHidden` INTEGER NOT NULL, `displayOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `student_field_values` (`studentId` TEXT NOT NULL, `fieldId` TEXT NOT NULL, `value` TEXT NOT NULL, PRIMARY KEY(`studentId`, `fieldId`))")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE courses ADD COLUMN year TEXT NOT NULL DEFAULT '2024'")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "attendance_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
