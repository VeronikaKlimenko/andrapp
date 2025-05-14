package com.example.lr5

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.example.lr5.ui.theme.Lr5Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Lr5Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Surface(modifier = Modifier.padding(innerPadding)) {
                        ShoppingListScreen()
                    }
                }
            }
        }
    }
}

@Entity(tableName = "shopping_items")
data class ShoppingItem(
    val name: String,
    val isBought: Boolean = false,
    @PrimaryKey(autoGenerate = true) val id: Int = 0
)

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items")
    fun getAllItems(): List<ShoppingItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertItem(item: ShoppingItem)

    @Update
    fun updateItem(item: ShoppingItem)

    @Delete
    fun deleteItem(item: ShoppingItem)
}

@Database(entities = [ShoppingItem::class], version = 1)
abstract class ShoppingDatabase : RoomDatabase() {
    abstract fun shoppingDao(): ShoppingDao

    companion object {
        @Volatile
        private var INSTANCE: ShoppingDatabase? = null

        fun getInstance(context: Context): ShoppingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShoppingDatabase::class.java,
                    "shopping_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}


class ShoppingListViewModel(application: Application) : AndroidViewModel(application) {
    private val dao: ShoppingDao = ShoppingDatabase.getInstance(application).shoppingDao()
    private val _shoppingList = MutableStateFlow(emptyList<ShoppingItem>())
    val shoppingList = _shoppingList.asStateFlow()

    init {
        loadShoppingList()
    }

    private fun loadShoppingList() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = dao.getAllItems()
            _shoppingList.update { items }
        }
    }

    fun addItem(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val newItem = ShoppingItem(name = name)
            dao.insertItem(newItem)
            loadShoppingList()
        }
    }

    fun deleteItem(index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = _shoppingList.value[index]
            dao.deleteItem(item)
            loadShoppingList()
        }
    }

    fun toggleBought(index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = _shoppingList.value[index]
            val updatedItem = item.copy(isBought = !item.isBought)
            dao.updateItem(updatedItem)
            _shoppingList.update {
                it.toMutableList().apply {
                    set(index, updatedItem)
                }
            }
        }
    }
}

@Composable
fun ShoppingItemCard(
    item: ShoppingItem,
    modifier: Modifier = Modifier,
    onToggleBought: () -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceDim)
            .clickable { onToggleBought() }
            .padding(16.dp)
    ) {
        Checkbox(
            checked = item.isBought,
            onCheckedChange = {
                onToggleBought()
            }
        )
        Text(
            text = item.name,
            fontSize = 18.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

class ShoppingListViewModelFactory(private val application: Application) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShoppingListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShoppingListViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@Composable
fun ShoppingListScreen(
    viewModel: ShoppingListViewModel = viewModel(
        factory = ShoppingListViewModelFactory(LocalContext.current.applicationContext as Application)
    )
) {
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val shoppingList by viewModel.shoppingList.collectAsState()
        Text(
            text = stringResource(R.string.total_items) + ": ${shoppingList.size}",
            style = MaterialTheme.typography.titleLarge
        )

        var text by remember { mutableStateOf("") }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.add_item)) }
        )

        var sort by remember {
            mutableStateOf<Sort>(Sort.ByDate)
        }
        var sortOrder by remember {
            mutableStateOf<SortOrder>(SortOrder.Descending)
        }

        Row(
            modifier = Modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = {
                if (text.isNotBlank()) {
                    viewModel.addItem(text)
                    text = ""
                }
            }) {
                Text(stringResource(R.string.add))
            }

            Spacer(Modifier.width(16.dp))

            SortingButton(
                sort = sort,
                order = sortOrder,
                onSortChange = {
                    sort = it
                },
                onSortOrderChange = {
                    sortOrder = it
                }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            itemsIndexed(
                items = shoppingList.let {
                    when (sortOrder) {
                        SortOrder.Ascending -> {
                            when (sort) {
                                Sort.ByName -> it.sortedBy { it.name }
                                Sort.ByDate -> it
                            }
                        }

                        SortOrder.Descending -> {
                            when (sort) {
                                Sort.ByName -> it.sortedByDescending { it.name }
                                Sort.ByDate -> it.reversed()
                            }
                        }
                    }
                },
                key = { ix, item -> "$ix-${item.name}" }
            ) { ix, item ->
                Row(
                    modifier = Modifier.animateItem(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShoppingItemCard(item, Modifier.weight(1f)) {
                        viewModel.toggleBought(ix)
                    }

                    Spacer(Modifier.width(8.dp))

                    IconButton(
                        onClick = { viewModel.deleteItem(ix) }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Delete ${item.name}"
                        )
                    }
                }
            }
        }
    }
}

enum class Sort {
    ByName, ByDate
}

enum class SortOrder {
    Ascending, Descending
}

@Composable
fun SortingButton(
    sort: Sort,
    order: SortOrder,
    onSortChange: (Sort) -> Unit,
    onSortOrderChange: (SortOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    Box {
        var isMenuExpanded by remember {
            mutableStateOf(false)
        }

        IconButton(
            onClick = {
                isMenuExpanded = true
            },
            modifier = modifier
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Sort,
                contentDescription = stringResource(R.string.sort)
            )
        }

        DropdownMenu(
            expanded = isMenuExpanded,
            onDismissRequest = {
                isMenuExpanded = false
            },
            shape = ShapeDefaults.Medium,
            modifier = Modifier
                .width(IntrinsicSize.Min)
        ) {
            SortButton(
                isSelected = sort == Sort.ByName,
                text = stringResource(R.string.name),
                onClick = {
                    onSortChange(Sort.ByName)
                }
            )

            SortButton(
                isSelected = sort == Sort.ByDate,
                text = stringResource(R.string.date),
                onClick = {
                    onSortChange(Sort.ByDate)
                }
            )

            SortOrderButtonsRow(
                order = order,
                onClick = onSortOrderChange,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}


@Composable
fun SortButton(
    isSelected: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(ShapeDefaults.Small)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else Color.Transparent
            )
            .clickable {
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SortOrderButtonsRow(
    order: SortOrder,
    onClick: (SortOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        val context = LocalContext.current
        SortOrderButton(
            isSelected = order == SortOrder.Ascending,
            imageVector = Icons.Rounded.ArrowUpward,
            contentDescription = context.resources.getString(R.string.sort_ascending),
            onClick = {
                onClick(SortOrder.Ascending)
            }
        )

        Spacer(modifier = Modifier.width(8.dp))

        SortOrderButton(
            isSelected = order == SortOrder.Descending,
            imageVector = Icons.Rounded.ArrowDownward,
            contentDescription = context.resources.getString(R.string.sort_descending),
            onClick = {
                onClick(SortOrder.Descending)
            }
        )
    }
}

@Composable
fun SortOrderButton(
    isSelected: Boolean,
    imageVector: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
                color = if (isSelected) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else Color.Transparent
            )
            .clickable {
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ShoppingListScreenPreview() {
    ShoppingListScreen()
}

//@Preview(showBackground = true)
@Composable
private fun ShoppingItemCardPreview() {
    var toggleState by remember {
        mutableStateOf(false)
    }
    ShoppingItemCard(
        ShoppingItem("Milk", toggleState)
    ) {
        toggleState = !toggleState
    }
}
