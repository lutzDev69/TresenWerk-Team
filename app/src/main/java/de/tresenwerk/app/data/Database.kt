package de.tresenwerk.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Transaction
    @Query("SELECT * FROM recipes ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<RecipeWithIngredients>>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observe(id: Long): Flow<RecipeWithIngredients?>

    @Transaction
    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun get(id: Long): RecipeWithIngredients?

    @Transaction
    @Query("SELECT * FROM recipes ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<RecipeWithIngredients>

    @Insert
    suspend fun insert(recipe: Recipe): Long

    @Update
    suspend fun update(recipe: Recipe)

    @Insert
    suspend fun insertIngredients(list: List<Ingredient>)

    @Query("DELETE FROM ingredients WHERE recipeId = :recipeId")
    suspend fun deleteIngredients(recipeId: Long)

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun deleteRecipeRow(id: Long)

    @Query("UPDATE recipes SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    /** Speichert Rezept und Zutaten in einem Rutsch. Gibt die Rezept-ID zurück. */
    @Transaction
    suspend fun save(recipe: Recipe, ingredients: List<Ingredient>): Long {
        val id = if (recipe.id == 0L) insert(recipe) else recipe.id.also { update(recipe) }
        deleteIngredients(id)
        insertIngredients(ingredients.mapIndexed { i, ing -> ing.copy(id = 0, recipeId = id, position = i) })
        return id
    }

    @Transaction
    suspend fun delete(id: Long) {
        deleteIngredients(id)
        deleteRecipeRow(id)
    }
}

@Dao
interface BottleDao {
    @Query("SELECT * FROM bottles ORDER BY brand COLLATE NOCASE, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Bottle>>

    @Query("SELECT * FROM bottles WHERE id = :id")
    suspend fun get(id: Long): Bottle?

    @Query("SELECT * FROM bottles ORDER BY brand COLLATE NOCASE, name COLLATE NOCASE")
    suspend fun getAll(): List<Bottle>

    @Insert
    suspend fun insert(bottle: Bottle): Long

    @Update
    suspend fun update(bottle: Bottle)

    @Query("DELETE FROM bottles WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [Recipe::class, Ingredient::class, Bottle::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recipes(): RecipeDao
    abstract fun bottles(): BottleDao

    companion object {
        /** Version 2: Flaschen bekommen ein Barcode-Feld. Vorhandene Daten bleiben erhalten. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bottles ADD COLUMN barcode TEXT")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "tresenwerk.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}
