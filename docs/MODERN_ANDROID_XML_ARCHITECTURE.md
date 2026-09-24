# Modern Android XML Architecture

## 1. Purpose

This document defines a modern, reusable Android architecture that keeps the familiar XML, Activity/Fragment, and RecyclerView approach while using current lifecycle, state-management, networking, persistence, and testing practices.

Use this guide for new applications that require View-based Android UI instead of Jetpack Compose.

The baseline stack is:

- Kotlin
- XML layouts and View Binding
- A single Activity with Fragment destinations where practical
- RecyclerView with `ListAdapter` and `DiffUtil`
- Immutable Kotlin data classes
- Screen-level `ViewModel`
- `StateFlow` and unidirectional data flow
- Coroutines and structured concurrency
- Repository and data-source boundaries
- Retrofit and OkHttp
- Moshi or Kotlin serialization
- Navigation Component
- Hilt dependency injection
- Room and DataStore when local persistence is required

Do not treat every item as mandatory. A small application still needs clear UI and data layers, but it does not need an artificial domain layer or offline database.

## 2. Architecture at a glance

```text
XML layout
    |
    v
Fragment / Activity
    | observes immutable StateFlow
    | sends user events
    v
ViewModel
    |
    | calls suspend functions / collects Flow
    v
Repository interface
    |
    v
Repository implementation
    |
    +--------------------+
    |                    |
    v                    v
Remote data source   Local data source
Retrofit / OkHttp    Room / DataStore
    |                    |
    +----------+---------+
               |
               v
         Domain models
               |
               v
      UiState -> RecyclerView ListAdapter
```

State travels down toward the UI. User events travel up toward the ViewModel. The Fragment renders state but does not own application data or business rules.

## 3. Core architectural rules

1. The UI layer displays immutable state and forwards user events.
2. A screen-level ViewModel owns screen state and survives configuration changes.
3. The ViewModel does not hold an Activity, Fragment, View, `Context`, or `Resources` reference.
4. Repositories expose application data and centralize changes to it.
5. Each data source works with one source: network, database, file, or preferences.
6. Retrofit DTOs do not leak directly into a complex UI or domain layer.
7. Coroutines replace raw threads and nested callbacks.
8. The UI collects Flow only while its view lifecycle is active.
9. RecyclerView adapters render supplied models and emit typed click events.
10. Navigation is performed by the UI after receiving state or events from the ViewModel.
11. Long-lived application data has one source of truth.
12. Dependencies are passed through constructors, normally using Hilt.

## 4. Recommended project structure

For a small or medium single-module application:

```text
com.example.app/
|-- App.kt
|-- di/
|   |-- NetworkModule.kt
|   `-- RepositoryModule.kt
|-- data/
|   |-- remote/
|   |   |-- ProductApi.kt
|   |   |-- ProductRemoteDataSource.kt
|   |   `-- dto/
|   |       `-- ProductDto.kt
|   |-- local/
|   |   |-- ProductDao.kt
|   |   `-- ProductEntity.kt
|   |-- mapper/
|   |   `-- ProductMapper.kt
|   `-- repository/
|       `-- DefaultProductRepository.kt
|-- domain/
|   |-- model/
|   |   `-- Product.kt
|   |-- repository/
|   |   `-- ProductRepository.kt
|   `-- usecase/
|       `-- GetProductsUseCase.kt       # optional
|-- ui/
|   `-- productlist/
|       |-- ProductListFragment.kt
|       |-- ProductListViewModel.kt
|       |-- ProductListUiState.kt
|       |-- ProductListAction.kt
|       `-- ProductAdapter.kt
`-- navigation/

res/
|-- layout/
|   |-- fragment_product_list.xml
|   `-- item_product.xml
|-- navigation/
|   `-- main_nav_graph.xml
`-- values/
```

The domain layer is optional. Omit it when a repository can return simple application models directly without duplicated or reusable business logic.

For a large multi-module application, organize modules by feature and keep shared network, database, model, and design-system code in focused core modules.

## 5. Dependency direction

Dependencies point inward toward stable abstractions:

```text
UI -> domain/repository abstractions <- data implementations
                                      |
                                      v
                               external systems
```

The UI may know a repository interface through its ViewModel. It must not know Retrofit, OkHttp, Room DAO, JSON keys, or database columns.

The data layer may know DTOs and entities. It must not know Android Views or RecyclerView adapters.

## 6. Gradle configuration

Use the current stable versions from the Android documentation and library release notes. Keep versions in `libs.versions.toml` instead of scattering them across module files.

Enable View Binding:

```kotlin
android {
    buildFeatures {
        viewBinding = true
    }
}
```

Typical dependencies include:

```kotlin
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)

    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.recyclerview)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.codegen)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
```

Add Room and DataStore only when the application requires structured local data or preferences.

## 7. Application and dependency injection

Create an Application class for Hilt:

```kotlin
@HiltAndroidApp
class ExampleApplication : Application()
```

Register it in the manifest:

```xml
<application
    android:name=".ExampleApplication"
    ... />
```

Hilt provides dependencies through constructors and modules. Avoid service-locator singletons and static `getInstance()` methods.

## 8. Domain model

The application model describes data in terms the application understands, not in terms of JSON or database storage.

```kotlin
data class Product(
    val id: String,
    val name: String,
    val price: Double,
    val imageUrl: String,
    val isFavorite: Boolean,
)
```

Model guidelines:

- Prefer immutable `val` properties.
- Use meaningful types instead of encoding booleans and numbers as strings.
- Do not put `Context`, `View`, `Drawable`, callbacks, or adapters in models.
- Give each layer its own model when their responsibilities differ materially.
- Avoid creating duplicate models when a simple application does not benefit from them.

Kotlin data classes replace traditional mutable Java POJOs while serving the same data-model purpose.

## 9. Network layer

The modern replacement for a custom `HttpURLConnection` class is normally:

- Retrofit for typed API declarations and request execution.
- OkHttp for connections, interceptors, headers, caching, and timeouts.
- Moshi or Kotlin serialization for JSON conversion.
- A network DI module for constructing and configuring shared instances.

### 9.1 Transport DTO

DTOs mirror the server contract and remain inside the data layer.

```kotlin
@JsonClass(generateAdapter = true)
data class ProductDto(
    @Json(name = "id") val id: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "price") val price: Double?,
    @Json(name = "imageUrl") val imageUrl: String?,
    @Json(name = "isFavorite") val isFavorite: Boolean?,
)

@JsonClass(generateAdapter = true)
data class ProductListResponse(
    @Json(name = "success") val success: Boolean,
    @Json(name = "data") val data: List<ProductDto>?,
    @Json(name = "message") val message: String?,
)
```

Nullable DTO fields protect parsing from incomplete server data. Convert them into non-null application defaults or reject invalid records in the mapper.

### 9.2 Retrofit API interface

```kotlin
interface ProductApi {
    @GET("products")
    suspend fun getProducts(
        @Query("page") page: Int,
    ): ProductListResponse

    @POST("products/{id}/favorite")
    suspend fun setFavorite(
        @Path("id") productId: String,
        @Body request: FavoriteRequest,
    )
}

@JsonClass(generateAdapter = true)
data class FavoriteRequest(
    @Json(name = "favorite") val favorite: Boolean,
)
```

Retrofit `suspend` functions already execute network work away from the main thread. Do not wrap them in `Thread` or `withContext(Dispatchers.IO)` merely to make the request.

### 9.3 Authentication interceptor

```kotlin
interface AuthTokenProvider {
    fun currentToken(): String
}

class AuthInterceptor @Inject constructor(
    private val authTokenProvider: AuthTokenProvider,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = authTokenProvider.currentToken()
        val request = chain.request()
            .newBuilder()
            .apply {
                if (token.isNotBlank()) {
                    header("Authorization", "Bearer $token")
                }
            }
            .build()

        return chain.proceed(request)
    }
}
```

Do not add authentication headers independently in every Activity, Fragment, or adapter.

### 9.4 Central network module

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        moshi: Moshi,
    ): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    @Provides
    @Singleton
    fun provideProductApi(retrofit: Retrofit): ProductApi =
        retrofit.create(ProductApi::class.java)
}
```

Keep the base URL in build configuration, not hard-coded across feature files. Use a release-safe logging policy and never log tokens, passwords, or sensitive response bodies.

## 10. Mapping DTOs to application models

```kotlin
fun ProductDto.toDomain(): Product? {
    val safeId = id?.takeIf { it.isNotBlank() } ?: return null

    return Product(
        id = safeId,
        name = name.orEmpty(),
        price = price ?: 0.0,
        imageUrl = imageUrl.orEmpty(),
        isFavorite = isFavorite ?: false,
    )
}
```

Mapping provides a boundary where the application can:

- Reject invalid records.
- Normalize inconsistent server values.
- Rename fields.
- Convert timestamps, enums, and identifiers.
- Avoid nullable values throughout the UI.

## 11. Remote data source

A data source communicates with exactly one external source.

```kotlin
class ProductRemoteDataSource @Inject constructor(
    private val api: ProductApi,
) {
    suspend fun getProducts(page: Int): List<ProductDto> {
        val response = api.getProducts(page)
        if (!response.success) {
            throw ApiException(response.message ?: "Unable to load products")
        }
        return response.data.orEmpty()
    }

    suspend fun setFavorite(productId: String, favorite: Boolean) {
        api.setFavorite(productId, FavoriteRequest(favorite))
    }
}

class ApiException(message: String) : Exception(message)
```

The data source does not expose views, UI messages, or navigation behavior.

## 12. Repository

The repository is the public data-layer boundary for a data type.

```kotlin
interface ProductRepository {
    suspend fun getProducts(page: Int): List<Product>
    suspend fun setFavorite(productId: String, favorite: Boolean)
}
```

```kotlin
class DefaultProductRepository @Inject constructor(
    private val remoteDataSource: ProductRemoteDataSource,
) : ProductRepository {

    override suspend fun getProducts(page: Int): List<Product> =
        remoteDataSource.getProducts(page).mapNotNull(ProductDto::toDomain)

    override suspend fun setFavorite(productId: String, favorite: Boolean) {
        remoteDataSource.setFavorite(productId, favorite)
    }
}
```

Bind the implementation with Hilt:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindProductRepository(
        implementation: DefaultProductRepository,
    ): ProductRepository
}
```

Repository responsibilities include:

- Exposing application data.
- Coordinating remote and local sources.
- Defining the source of truth.
- Resolving cache and synchronization behavior.
- Containing data-related business rules.

A repository should not merely mirror every Retrofit method without adding a useful boundary.

## 13. Optional use cases

Add a use case when business logic is complex, reused by multiple ViewModels, or combines several repositories.

```kotlin
class GetProductsUseCase @Inject constructor(
    private val productRepository: ProductRepository,
) {
    suspend operator fun invoke(page: Int): List<Product> =
        productRepository.getProducts(page)
            .sortedBy(Product::name)
}
```

Do not create pass-through use cases solely to increase the number of layers.

## 14. Immutable UI state

Model everything necessary to render a screen in a single immutable state.

```kotlin
sealed interface ProductListUiState {
    data object Loading : ProductListUiState

    data class Content(
        val products: List<Product>,
        val isRefreshing: Boolean = false,
        val isAppending: Boolean = false,
        val canLoadMore: Boolean = true,
    ) : ProductListUiState

    data object Empty : ProductListUiState

    data class Error(
        val message: String,
        val canRetry: Boolean = true,
    ) : ProductListUiState
}
```

An alternative is one data class containing `isLoading`, data, and error fields. Use a sealed hierarchy when the states are mutually exclusive. Use a data class when the screen can display several conditions simultaneously.

## 15. ViewModel

The ViewModel receives events, calls the repository, and produces UI state.

```kotlin
@HiltViewModel
class ProductListViewModel @Inject constructor(
    private val productRepository: ProductRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProductListUiState>(
        ProductListUiState.Loading,
    )
    val uiState: StateFlow<ProductListUiState> = _uiState.asStateFlow()

    private var currentPage = 0
    private var requestRunning = false
    private var canLoadMore = true

    init {
        loadProducts()
    }

    fun onRefresh() {
        if (requestRunning) return
        currentPage = 0
        canLoadMore = true
        loadProducts(refresh = true)
    }

    fun onRetry() {
        if (!requestRunning) loadProducts()
    }

    fun onLoadMore() {
        if (!requestRunning && canLoadMore) {
            loadProducts(append = true)
        }
    }

    private fun loadProducts(
        refresh: Boolean = false,
        append: Boolean = false,
    ) {
        viewModelScope.launch {
            requestRunning = true

            val previous = (_uiState.value as? ProductListUiState.Content)
                ?.products
                .orEmpty()

            _uiState.value = when {
                refresh && previous.isNotEmpty() -> ProductListUiState.Content(
                    products = previous,
                    isRefreshing = true,
                )
                append && previous.isNotEmpty() -> ProductListUiState.Content(
                    products = previous,
                    isAppending = true,
                )
                else -> ProductListUiState.Loading
            }

            val nextPage = if (append) currentPage + 1 else 1

            try {
                val incoming = productRepository.getProducts(nextPage)
                currentPage = nextPage
                canLoadMore = incoming.isNotEmpty()
                val merged = if (append) {
                    (previous + incoming).distinctBy(Product::id)
                } else {
                    incoming
                }

                _uiState.value = if (merged.isEmpty()) {
                    ProductListUiState.Empty
                } else {
                    ProductListUiState.Content(
                        products = merged,
                        canLoadMore = canLoadMore,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                _uiState.value = if (previous.isNotEmpty()) {
                    ProductListUiState.Content(products = previous)
                } else {
                    ProductListUiState.Error(
                        message = error.toUserMessage(),
                    )
                }
            } finally {
                requestRunning = false
            }
        }
    }
}
```

In a production application, error-to-message conversion should use typed failures or error codes. Avoid placing Android `Context` or localized resource lookup inside a ViewModel.

When UI state is derived from repository Flows, prefer `stateIn` with `SharingStarted.WhileSubscribed(5_000)` instead of manually collecting forever in `init`.

## 16. UI actions and one-time effects

Persistent state belongs in `StateFlow`. A transient action such as navigation can be handled in one of two ways:

1. The Fragment navigates directly from a click when no business decision is required.
2. The ViewModel emits a carefully modeled effect when navigation depends on business logic.

Do not represent required user-visible state as a lossy event. If an error must remain visible after rotation, keep it in `UiState` until the user dismisses or retries it.

Example input action:

```kotlin
sealed interface ProductListAction {
    data object Refresh : ProductListAction
    data object Retry : ProductListAction
    data object LoadMore : ProductListAction
    data class ProductSelected(val id: String) : ProductListAction
}
```

For a simple screen, individual ViewModel functions are equally acceptable and easier to read.

## 17. XML layout

`res/layout/fragment_product_list.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.swiperefreshlayout.widget.SwipeRefreshLayout
        android:id="@+id/swipeRefresh"
        android:layout_width="0dp"
        android:layout_height="0dp"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/productList"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:clipToPadding="false"
            android:padding="@dimen/list_padding"
            app:layoutManager="androidx.recyclerview.widget.LinearLayoutManager"
            tools:listitem="@layout/item_product" />

    </androidx.swiperefreshlayout.widget.SwipeRefreshLayout>

    <ProgressBar
        android:id="@+id/loadingIndicator"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:visibility="gone"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <TextView
        android:id="@+id/messageText"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:visibility="gone"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

    <Button
        android:id="@+id/retryButton"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/retry"
        android:visibility="gone"
        app:layout_constraintTop_toBottomOf="@id/messageText"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintEnd_toEndOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
```

Include the `tools` namespace on the root when using `tools:listitem`. Keep strings, colors, spacing, and typography in resources or a shared design system.

## 18. RecyclerView item XML

`res/layout/item_product.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginBottom="@dimen/item_spacing">

    <androidx.constraintlayout.widget.ConstraintLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="@dimen/item_padding">

        <ImageView
            android:id="@+id/productImage"
            android:layout_width="72dp"
            android:layout_height="72dp"
            android:contentDescription="@null"
            app:layout_constraintTop_toTopOf="parent"
            app:layout_constraintBottom_toBottomOf="parent"
            app:layout_constraintStart_toStartOf="parent" />

        <TextView
            android:id="@+id/productName"
            style="@style/TextAppearance.Material3.TitleMedium"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_marginStart="12dp"
            app:layout_constraintTop_toTopOf="parent"
            app:layout_constraintStart_toEndOf="@id/productImage"
            app:layout_constraintEnd_toEndOf="parent" />

        <TextView
            android:id="@+id/productPrice"
            style="@style/TextAppearance.Material3.BodyMedium"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            app:layout_constraintTop_toBottomOf="@id/productName"
            app:layout_constraintStart_toStartOf="@id/productName"
            app:layout_constraintEnd_toEndOf="parent" />

    </androidx.constraintlayout.widget.ConstraintLayout>

</com.google.android.material.card.MaterialCardView>
```

## 19. ListAdapter and ViewHolder

Use View Binding in ViewHolders and `DiffUtil` for minimal, stable updates.

```kotlin
class ProductAdapter(
    private val onProductClick: (Product) -> Unit,
) : ListAdapter<Product, ProductAdapter.ProductViewHolder>(ProductDiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemProductBinding.inflate(inflater, parent, false)
        return ProductViewHolder(binding, onProductClick)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ProductViewHolder(
        private val binding: ItemProductBinding,
        private val onProductClick: (Product) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Product) = with(binding) {
            productName.text = item.name
            productPrice.text = root.context.getString(
                R.string.product_price,
                item.price,
            )

            productImage.load(item.imageUrl) {
                crossfade(true)
                placeholder(R.drawable.image_placeholder)
                error(R.drawable.image_error)
            }

            root.setOnClickListener { onProductClick(item) }
        }
    }

    private object ProductDiffCallback : DiffUtil.ItemCallback<Product>() {
        override fun areItemsTheSame(oldItem: Product, newItem: Product): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Product, newItem: Product): Boolean =
            oldItem == newItem
    }
}
```

Adapter rules:

- Submit immutable lists through `submitList()`.
- Compare stable identifiers in `areItemsTheSame`.
- Compare displayed content in `areContentsTheSame`.
- Forward clicks with lambdas or typed listener interfaces.
- Do not call APIs, repositories, DAOs, or navigation controllers from the adapter.
- Do not keep a long-lived Activity reference.
- Use multiple typed ViewHolders or a sealed row model for heterogeneous lists.

## 20. Fragment with View Binding and lifecycle-aware state collection

```kotlin
@AndroidEntryPoint
class ProductListFragment : Fragment(R.layout.fragment_product_list) {

    private var _binding: FragmentProductListBinding? = null
    private val binding get() = checkNotNull(_binding)

    private val viewModel: ProductListViewModel by viewModels()

    private val productAdapter = ProductAdapter { product ->
        val direction = ProductListFragmentDirections
            .actionProductListToProductDetail(product.id)
        findNavController().navigate(direction)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentProductListBinding.bind(view)

        configureList()
        configureActions()
        observeState()
    }

    private fun configureList() = with(binding.productList) {
        adapter = productAdapter
        setHasFixedSize(true)

        addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val manager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisible = manager.findLastVisibleItemPosition()
                if (lastVisible >= productAdapter.itemCount - LOAD_MORE_THRESHOLD) {
                    viewModel.onLoadMore()
                }
            }
        })
    }

    private fun configureActions() = with(binding) {
        swipeRefresh.setOnRefreshListener(viewModel::onRefresh)
        retryButton.setOnClickListener { viewModel.onRetry() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    private fun render(state: ProductListUiState) = with(binding) {
        loadingIndicator.isVisible = state is ProductListUiState.Loading
        retryButton.isVisible = state is ProductListUiState.Error
        messageText.isVisible = state is ProductListUiState.Empty ||
            state is ProductListUiState.Error
        productList.isVisible = state is ProductListUiState.Content

        when (state) {
            ProductListUiState.Loading -> Unit

            ProductListUiState.Empty -> {
                swipeRefresh.isRefreshing = false
                messageText.setText(R.string.products_empty)
                productAdapter.submitList(emptyList())
            }

            is ProductListUiState.Content -> {
                swipeRefresh.isRefreshing = state.isRefreshing
                productAdapter.submitList(state.products)
            }

            is ProductListUiState.Error -> {
                swipeRefresh.isRefreshing = false
                messageText.text = state.message
            }
        }
    }

    override fun onDestroyView() {
        binding.productList.adapter = null
        _binding = null
        super.onDestroyView()
    }

    private companion object {
        const val LOAD_MORE_THRESHOLD = 5
    }
}
```

Important lifecycle points:

- Bind views in `onViewCreated` and clear the binding in `onDestroyView`.
- Collect Flow with the Fragment's view lifecycle, not the Fragment lifecycle.
- `repeatOnLifecycle` cancels collection below `STARTED` and restarts it when active.
- The Fragment renders the complete state instead of applying unrelated mutations from callbacks.

## 21. Activity role

Prefer one Activity hosting Fragment destinations for ordinary applications. Use additional Activities when a feature genuinely requires a separate task, window behavior, external entry point, or isolated flow.

The host Activity typically:

- Inflates its binding.
- Hosts the `NavHostFragment`.
- Connects top-level navigation UI.
- Handles window insets and app-wide UI concerns.

Feature loading, list state, and API calls should stay in feature ViewModels and repositories rather than the Activity.

## 22. Navigation

Use Navigation Component and typed arguments where practical.

```xml
<fragment
    android:id="@+id/productListFragment"
    android:name="com.example.app.ui.productlist.ProductListFragment">

    <action
        android:id="@+id/action_productList_to_productDetail"
        app:destination="@id/productDetailFragment" />
</fragment>

<fragment
    android:id="@+id/productDetailFragment"
    android:name="com.example.app.ui.productdetail.ProductDetailFragment">

    <argument
        android:name="productId"
        app:argType="string" />
</fragment>
```

Pass stable identifiers rather than large mutable objects. The destination loads authoritative data using the identifier and its repository.

## 23. Room and offline-first data

When local data is the source of truth, the UI observes Room through the repository and the network synchronizes into the database:

```text
Fragment <- StateFlow <- ViewModel <- Repository <- Room Flow
                                             ^
                                             |
                                      network synchronization
```

Example DAO:

```kotlin
@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name")
    fun observeProducts(): Flow<List<ProductEntity>>

    @Upsert
    suspend fun upsertAll(products: List<ProductEntity>)

    @Query("DELETE FROM products")
    suspend fun clear()
}
```

Do not make the UI choose between the database and network. That policy belongs in the repository.

## 24. DataStore and session state

Use DataStore for preferences and small durable settings. Keep access behind a data-layer class.

```kotlin
class SessionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.sessionDataStore

    val session: Flow<Session> = dataStore.data.map { preferences ->
        Session(
            userId = preferences[USER_ID].orEmpty(),
            token = preferences[AUTH_TOKEN].orEmpty(),
        )
    }

    suspend fun saveSession(session: Session) {
        dataStore.edit { preferences ->
            preferences[USER_ID] = session.userId
            preferences[AUTH_TOKEN] = session.token
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
```

If an interceptor requires synchronous token access, maintain an in-memory, application-scoped session snapshot that is populated from DataStore. Avoid blocking every OkHttp request with disk I/O.

Never commit tokens, API secrets, release signing passwords, or private keystores to source control.

## 25. Error model

Translate technical failures at layer boundaries.

```kotlin
sealed interface AppError {
    data object NoConnection : AppError
    data object Unauthorized : AppError
    data object Timeout : AppError
    data class Server(val code: Int, val message: String?) : AppError
    data class Unknown(val cause: Throwable) : AppError
}
```

The data layer can map `IOException`, `HttpException`, and API failures to typed errors. The ViewModel converts typed errors into UI state. The Fragment resolves localized strings where appropriate.

Do not show raw stack traces or server bodies to users. Do not silently swallow errors that affect required state.

## 26. Loading, refresh, pagination, and retry

Keep these concepts distinct:

- Initial loading: no content is available yet.
- Refreshing: current content remains visible while being updated.
- Appending: current content remains visible while the next page loads.
- Empty: the request succeeded but produced no displayable data.
- Error: the operation failed and the user may retry.

Pagination state belongs in the ViewModel or Paging library, not in the adapter.

For complex, very large, or database-backed paginated lists, use Paging 3 rather than implementing page counters manually.

## 27. Background work

Choose the API based on the work:

| Requirement | Mechanism |
|---|---|
| Work tied to a screen | `viewModelScope` coroutine |
| Work that must survive app exit and can be deferred | WorkManager |
| User-visible ongoing media playback | Foreground media service |
| User-visible long-running transfer | Foreground service or platform download API |
| Structured local persistence | Room |

Do not use raw threads for ordinary repository calls. Do not start a foreground service for short work that can finish inside a coroutine.

## 28. Testing strategy

### ViewModel test

Use a fake repository and coroutine test dispatcher:

```kotlin
class FakeProductRepository : ProductRepository {
    var products: List<Product> = emptyList()
    var failure: Throwable? = null

    override suspend fun getProducts(page: Int): List<Product> {
        failure?.let { throw it }
        return products
    }

    override suspend fun setFavorite(productId: String, favorite: Boolean) = Unit
}
```

Test at minimum:

- Loading followed by content.
- Loading followed by empty.
- Loading followed by error.
- Refresh preserving visible content.
- Pagination deduplication and termination.
- Retry behavior.

### Repository test

Use fake remote and local data sources. Verify mapping, caching policy, source-of-truth behavior, and error translation.

### Adapter test

Verify DiffUtil identity rules, view binding, and click forwarding. Keep adapter logic small so most behavior remains testable outside instrumentation.

### Navigation/UI test

Test critical navigation routes and a small number of high-value user journeys in CI.

Prefer fakes over mocks when a small fake can model the dependency clearly.

## 29. What not to do

Avoid these legacy patterns in new code:

- Starting raw threads in Activities or Fragments.
- Calling Retrofit directly from adapters.
- Parsing JSON inside a Fragment.
- Storing Views or Activities in static fields.
- Holding an Activity callback inside a ViewModel.
- Exposing mutable collections from a ViewModel.
- Calling `notifyDataSetChanged()` for every list update.
- Reading SharedPreferences throughout the UI.
- Using `AndroidViewModel` simply to obtain a `Context`.
- Embedding API base URLs and secrets in source files.
- Treating a callback as durable screen state.
- Adding a use case, repository, or mapper that has no meaningful responsibility.

## 30. New feature workflow

For a new network-backed RecyclerView feature:

1. Define the application/domain model.
2. Define the Retrofit DTO and API endpoint.
3. Map the DTO to the application model.
4. Add or extend the remote data source.
5. Define a repository interface and implementation.
6. Add a use case only if business logic warrants it.
7. Define immutable `UiState`.
8. Implement a screen-level ViewModel.
9. Create the screen and item XML layouts.
10. Create a View Binding `ListAdapter` with `DiffUtil`.
11. Create the Fragment and collect state lifecycle-aware.
12. Add navigation and typed arguments.
13. Test the mapper, repository, and ViewModel.
14. Add UI tests for critical interactions.

## 31. Architecture review checklist

A feature follows this architecture when:

- The Fragment only renders state and forwards events.
- The ViewModel exposes immutable `StateFlow` UI state.
- The ViewModel has no Android UI or lifecycle references.
- The adapter receives immutable models through `submitList()`.
- Retrofit DTOs remain in the data layer.
- JSON mapping is outside the UI.
- A repository owns data access policy.
- Network and database dependencies are constructor-injected.
- Flow collection uses the view lifecycle.
- Loading, empty, content, retry, refresh, and pagination states are explicit.
- Errors are typed or consistently translated.
- Important data has a single source of truth.
- Unit tests cover state production and data mapping.

## 32. Minimal version for a small application

Do not over-engineer a small application. Its complete path can be:

```text
XML + Fragment
      |
      v
ViewModel + UiState
      |
      v
Repository
      |
      v
Retrofit API
```

The following remain worthwhile even in the minimal version:

- View Binding
- ViewModel
- Immutable StateFlow UI state
- Coroutines
- Repository boundary
- Retrofit DTO mapping
- ListAdapter and DiffUtil
- Lifecycle-aware state collection
- Constructor injection, either Hilt or a small manual container

Add Room, DataStore, use cases, Paging, and multiple Gradle modules only when product requirements justify them.

## 33. Official references

- [Guide to app architecture](https://developer.android.com/topic/architecture)
- [Recommendations for Android architecture](https://developer.android.com/topic/architecture/recommendations)
- [UI layer and unidirectional data flow](https://developer.android.com/topic/architecture/ui-layer)
- [Data layer](https://developer.android.com/topic/architecture/data-layer)
- [ViewModel for Views](https://developer.android.com/topic/libraries/architecture/views/viewmodel)
- [View Binding](https://developer.android.com/topic/libraries/view-binding)
- [RecyclerView](https://developer.android.com/develop/ui/views/layout/recyclerview)
- [Hilt dependency injection](https://developer.android.com/training/dependency-injection/hilt-android)
- [Navigation with Views](https://developer.android.com/guide/navigation)
- [Room](https://developer.android.com/training/data-storage/room)
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
- [Coroutines best practices](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)
- [Paging 3](https://developer.android.com/topic/libraries/architecture/paging/v3-overview)

This document intentionally uses modern Android architecture with XML Views. Jetpack Compose is Android's recommended modern UI toolkit, but it is not required to apply layered architecture, ViewModel state management, repositories, coroutines, lifecycle-aware collection, or modern data practices.
