package com.example.sipangwingwi

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.sipangwingwi.theme.SipangwingwiDeepIndigo
import com.example.sipangwingwi.theme.SipangwingwiError
import com.example.sipangwingwi.theme.SipangwingwiInfo
import com.example.sipangwingwi.theme.SipangwingwiInk
import com.example.sipangwingwi.theme.SipangwingwiSuccess
import com.example.sipangwingwi.theme.SipangwingwiTheme
import com.example.sipangwingwi.theme.SipangwingwiWarning
import kotlinx.coroutines.launch

private enum class WorkflowTab(val label: String) {
    Home("Home"),
    Setup("Setup"),
    Inventory("Inventory"),
    Products("Products"),
    Suppliers("Suppliers"),
    Pos("POS"),
    Payments("M-Pesa"),
    Receipt("Receipt"),
    Staff("Staff")
}

private fun visibleTabsFor(role: UserRole?): List<WorkflowTab> =
    when (role) {
        UserRole.Admin -> WorkflowTab.entries
        UserRole.Manager -> listOf(
            WorkflowTab.Home,
            WorkflowTab.Inventory,
            WorkflowTab.Products,
            WorkflowTab.Suppliers,
            WorkflowTab.Pos,
            WorkflowTab.Payments,
            WorkflowTab.Receipt,
            WorkflowTab.Staff
        )
        UserRole.Worker -> listOf(
            WorkflowTab.Home,
            WorkflowTab.Pos,
            WorkflowTab.Payments,
            WorkflowTab.Receipt,
            WorkflowTab.Staff
        )
        null -> listOf(WorkflowTab.Home)
    }

@Composable
@Preview
fun App() {
    SipangwingwiTheme {
        SipangwingwiWorkflowApp(store = remember { InventoryDemoStore() })
    }
}

@Composable
private fun SipangwingwiWorkflowApp(store: InventoryDemoStore) {
    var tab by remember { mutableStateOf(WorkflowTab.Home) }
    var records by remember { mutableStateOf(store.productRecords()) }
    var movements by remember { mutableStateOf(store.movements()) }
    var suppliers by remember { mutableStateOf(store.suppliers()) }
    var cart by remember { mutableStateOf(store.cart()) }
    var receipt by remember { mutableStateOf(store.receipt()) }
    var mpesaState by remember { mutableStateOf(store.mpesaPaymentState()) }
    var session by remember { mutableStateOf(store.employeeSession()) }
    var setupComplete by remember { mutableStateOf(store.isSetupComplete()) }
    var business by remember { mutableStateOf(store.business()) }
    var branches by remember { mutableStateOf(store.branches()) }
    var activeBranch by remember { mutableStateOf(store.activeBranch()) }
    var accounts by remember { mutableStateOf(store.accounts()) }
    var employeeSummaries by remember { mutableStateOf(store.employeeSummaries()) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedProductId by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun refresh() {
        records = store.productRecords()
        movements = store.movements()
        suppliers = store.suppliers()
        cart = store.cart()
        receipt = store.receipt()
        mpesaState = store.mpesaPaymentState()
        session = store.employeeSession()
        setupComplete = store.isSetupComplete()
        business = store.business()
        branches = store.branches()
        activeBranch = store.activeBranch()
        accounts = store.accounts()
        employeeSummaries = store.employeeSummaries()
        if (selectedProductId.isBlank()) selectedProductId = records.firstOrNull()?.product?.id.orEmpty()
    }

    fun show(outcome: ReceiveGoodsOutcome) {
        when (outcome) {
            is ReceiveGoodsOutcome.Success -> {
                isError = false
                message = "${outcome.productName} updated."
            }

            is ReceiveGoodsOutcome.Failure -> {
                isError = true
                message = outcome.message
            }
        }
        refresh()
    }

    LaunchedEffect(store) {
        isLoading = true
        store.loadInventory()
            .onSuccess {
                refresh()
                message = null
                isError = false
            }
            .onFailure {
                refresh()
                message = "Could not load backend inventory. Start the server and try again."
                isError = true
            }
        isLoading = false
    }

    if (!setupComplete) {
        SetupFirstRunScreen(
            message = message,
            isError = isError,
            onComplete = { owner, phone, businessName, branchName, pin ->
                show(store.completeInitialSetup(owner, phone, businessName, branchName, pin))
            }
        )
        return
    }

    if (session == null) {
        LoginScreen(
            businessName = business?.name ?: "Sipangwingwi",
            accounts = accounts,
            message = message,
            isError = isError,
            onLogin = { employeeId, pin -> show(store.loginEmployee(employeeId, pin)) }
        )
        return
    }

    val visibleTabs = visibleTabsFor(session?.role)
    if (tab !in visibleTabs) tab = WorkflowTab.Home

    Scaffold(
        topBar = {
            AppTopBar(
                businessName = business?.name ?: "Sipangwingwi",
                branchName = activeBranch?.name ?: "Nairobi CBD",
                session = session,
                onLogout = {
                    store.logout()
                    refresh()
                    tab = WorkflowTab.Home
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeContentPadding()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SummaryStrip(records, movements.size, cart.sumOf { it.quantity }, session?.shiftOpen == true)
            WorkflowTabs(tabs = visibleTabs, selected = tab, onSelected = { tab = it })
            MessageBanner(message = if (isLoading) "Loading backend inventory..." else message, isError = isError)

            SurfacePanel(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when (tab) {
                    WorkflowTab.Home -> HomeScreen(
                        records = records,
                        suppliers = suppliers,
                        cart = cart,
                        receipt = receipt,
                        mpesaState = mpesaState,
                        session = session,
                        movementsCount = movements.size,
                        visibleTabs = visibleTabs,
                        onOpen = { tab = it }
                    )
                    WorkflowTab.Setup -> AdminSetupScreen(
                        business = business,
                        branches = branches,
                        activeBranch = activeBranch,
                        accounts = accounts,
                        onAddBranch = { name -> show(store.addBranch(name)) },
                        onSwitchBranch = { branchId -> show(store.switchBranch(branchId)) },
                        onCreateUser = { name, role, pin -> show(store.createUser(name, role, pin)) }
                    )
                    WorkflowTab.Inventory -> InventoryScreen(
                        records = records,
                        movements = movements,
                        selectedProductId = selectedProductId,
                        onSelectProduct = { selectedProductId = it },
                        onReceive = { quantity, supplierId, reference ->
                            scope.launch {
                                show(store.receiveGoods(selectedProductId, quantity, supplierId, reference))
                            }
                        }
                    )
                    WorkflowTab.Products -> ProductsScreen(
                        records = records,
                        onAdd = { name, barcode, sku -> show(store.addProduct(name, barcode, sku)) },
                        onUpdate = { id, name, barcode, sku -> show(store.updateProduct(id, name, barcode, sku)) }
                    )
                    WorkflowTab.Suppliers -> SuppliersScreen(
                        suppliers = suppliers,
                        onAdd = { name, phone -> show(store.addSupplier(name, phone)) }
                    )
                    WorkflowTab.Pos -> PosScreen(
                        records = records,
                        cart = cart,
                        onScan = { barcode -> show(store.scanBarcodeToCart(barcode)) },
                        onAdd = { productId -> show(store.addProductToCart(productId)) },
                        onClear = {
                            store.clearCart()
                            refresh()
                            message = "Cart cleared."
                            isError = false
                        },
                        onCashCheckout = { show(store.checkoutCash()) }
                    )
                    WorkflowTab.Payments -> PaymentsScreen(
                        cart = cart,
                        state = mpesaState,
                        onStart = { phone -> show(store.startMpesaPayment(phone)) },
                        onComplete = { show(store.completeMpesaPayment()) },
                        onFail = { show(store.failMpesaPayment()) }
                    )
                    WorkflowTab.Receipt -> ReceiptScreen(receipt)
                    WorkflowTab.Staff -> StaffScreen(
                        session = session,
                        summaries = employeeSummaries,
                        onLogin = { pin -> show(store.loginEmployee(pin)) },
                        onOpenShift = { show(store.openShift()) },
                        onCloseShift = { show(store.closeShift()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupFirstRunScreen(
    message: String?,
    isError: Boolean,
    onComplete: (String, String, String, String, String) -> Unit
) {
    var ownerName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var businessName by remember { mutableStateOf("") }
    var branchName by remember { mutableStateOf("") }
    var adminPin by remember { mutableStateOf("") }

    SipangwingwiTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeContentPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionHeader("Set up Sipangwingwi", "Create the owner account, business and first branch")
                MessageBanner(message, isError)
                OutlinedTextField(ownerName, { ownerName = it }, Modifier.fillMaxWidth(), label = { Text("Owner name") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("Owner phone") }, singleLine = true)
                OutlinedTextField(businessName, { businessName = it }, Modifier.fillMaxWidth(), label = { Text("Business name") }, singleLine = true)
                OutlinedTextField(branchName, { branchName = it }, Modifier.fillMaxWidth(), label = { Text("First branch") }, singleLine = true)
                OutlinedTextField(adminPin, { adminPin = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Admin PIN") }, singleLine = true)
                Button(onClick = { onComplete(ownerName, phone, businessName, branchName, adminPin) }) {
                    Text("Create account and business")
                }
                Text("After setup, login with the Admin PIN you create. Demo Manager PIN: 2222. Worker PIN: 1234.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LoginScreen(
    businessName: String,
    accounts: List<UserAccount>,
    message: String?,
    isError: Boolean,
    onLogin: (String, String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var selectedEmployeeId by remember { mutableStateOf(accounts.firstOrNull()?.employeeId.orEmpty()) }

    SipangwingwiTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeContentPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionHeader(businessName, "Login by role")
                MessageBanner(message, isError)
                SectionHeader("Choose user", "Select the account, then enter that user's PIN")
                accounts.forEach { account ->
                    val selected = account.employeeId == selectedEmployeeId
                    InfoRow(
                        title = if (selected) "* ${account.name}" else account.name,
                        body = "${account.role.displayName} | ${account.employeeId}",
                        action = "Select"
                    ) {
                        selectedEmployeeId = account.employeeId
                    }
                }
                OutlinedTextField(pin, { pin = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("PIN") }, singleLine = true)
                Button(onClick = { onLogin(selectedEmployeeId, pin) }) {
                    Text("Login")
                }
                InfoRow("Admin", "Use the PIN created during setup. Full setup, branch and management access.")
                InfoRow("Manager", "PIN 2222. Inventory, products, suppliers, POS and payments.")
                InfoRow("Worker", "PIN 1234. POS, payments, receipts and shift only.")
            }
        }
    }
}

@Composable
private fun HomeScreen(
    records: List<InventoryProductRecord>,
    suppliers: List<SupplierRecord>,
    cart: List<CartLine>,
    receipt: ReceiptPreview?,
    mpesaState: MpesaPaymentState,
    session: EmployeeSession?,
    movementsCount: Int,
    visibleTabs: List<WorkflowTab>,
    onOpen: (WorkflowTab) -> Unit
) {
    val totalStock = records.sumOf { it.stock.quantityOnHand }
    val lowStock = records.count { it.stock.quantityOnHand < 10 }

    ScreenColumn {
        SectionHeader("Operations dashboard", "Choose the workflow you want to run")
        if (WorkflowTab.Setup in visibleTabs) HomeActionCard(
            title = "Business setup",
            body = "Branches, active branch and role accounts",
            action = "Open setup",
            color = SipangwingwiDeepIndigo,
            onClick = { onOpen(WorkflowTab.Setup) }
        )
        if (WorkflowTab.Inventory in visibleTabs) HomeActionCard(
            title = "Inventory receiving",
            body = "$totalStock units in stock | $lowStock low-stock items | $movementsCount movements",
            action = "Receive goods",
            color = SipangwingwiSuccess,
            onClick = { onOpen(WorkflowTab.Inventory) }
        )
        if (WorkflowTab.Products in visibleTabs) HomeActionCard(
            title = "Products",
            body = "${records.size} products | create, edit, barcode and SKU",
            action = "Manage products",
            color = SipangwingwiInfo,
            onClick = { onOpen(WorkflowTab.Products) }
        )
        if (WorkflowTab.Suppliers in visibleTabs) HomeActionCard(
            title = "Suppliers",
            body = "${suppliers.size} suppliers | create supplier records for deliveries",
            action = "Manage suppliers",
            color = SipangwingwiDeepIndigo,
            onClick = { onOpen(WorkflowTab.Suppliers) }
        )
        if (WorkflowTab.Pos in visibleTabs) HomeActionCard(
            title = "Point of sale",
            body = "${cart.sumOf { it.quantity }} cart units | barcode entry and checkout",
            action = "Open POS",
            color = SipangwingwiWarning,
            onClick = { onOpen(WorkflowTab.Pos) }
        )
        if (WorkflowTab.Payments in visibleTabs) HomeActionCard(
            title = "M-Pesa payments",
            body = "State: $mpesaState | simulated STK confirmation",
            action = "Open M-Pesa",
            color = SipangwingwiInfo,
            onClick = { onOpen(WorkflowTab.Payments) }
        )
        if (WorkflowTab.Receipt in visibleTabs) HomeActionCard(
            title = "Receipts",
            body = receipt?.let { "Latest: ${it.receiptNumber} via ${it.paymentMethod}" } ?: "No completed receipt yet",
            action = "View receipt",
            color = SipangwingwiSuccess,
            onClick = { onOpen(WorkflowTab.Receipt) }
        )
        if (WorkflowTab.Staff in visibleTabs) HomeActionCard(
            title = "Staff and shifts",
            body = session?.let { "${it.employeeId} | Shift ${if (it.shiftOpen) "open" else "closed"}" } ?: "No employee logged in",
            action = "Open staff",
            color = SipangwingwiDeepIndigo,
            onClick = { onOpen(WorkflowTab.Staff) }
        )
    }
}

@Composable
private fun HomeActionCard(
    title: String,
    body: String,
    action: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(54.dp)
                    .background(color, RoundedCornerShape(8.dp))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onClick) {
                Text(action)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AppTopBar(
    businessName: String,
    branchName: String,
    session: EmployeeSession?,
    onLogout: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(businessName, fontWeight = FontWeight.Bold)
                Text("$branchName | ${session?.role?.displayName ?: ""}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        actions = {
            TextButton(onClick = onLogout) { Text("Logout") }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = SipangwingwiInk
        )
    )
}

@Composable
private fun WorkflowTabs(tabs: List<WorkflowTab>, selected: WorkflowTab, onSelected: (WorkflowTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEach { tab ->
            val active = tab == selected
            Button(
                onClick = { onSelected(tab) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(tab.label, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SummaryStrip(records: List<InventoryProductRecord>, movementsCount: Int, cartUnits: Long, shiftOpen: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        SummaryTile("Products", records.size.toString(), SipangwingwiInfo, Modifier.weight(1f))
        SummaryTile("Stock", records.sumOf { it.stock.quantityOnHand }.toString(), SipangwingwiSuccess, Modifier.weight(1f))
        SummaryTile("Cart", cartUnits.toString(), SipangwingwiWarning, Modifier.weight(1f))
        SummaryTile("Shift", if (shiftOpen) "Open" else "Closed", SipangwingwiDeepIndigo, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.height(68.dp), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp), shadowElevation = 1.dp) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleLarge, color = color, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun AdminSetupScreen(
    business: BusinessProfile?,
    branches: List<BranchProfile>,
    activeBranch: BranchProfile?,
    accounts: List<UserAccount>,
    onAddBranch: (String) -> Unit,
    onSwitchBranch: (String) -> Unit,
    onCreateUser: (String, UserRole, String) -> Unit
) {
    var branchName by remember { mutableStateOf("") }
    var employeeName by remember { mutableStateOf("") }
    var employeePin by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.Worker) }

    ScreenColumn {
        SectionHeader("Business setup", "Admin-only business, branch and account controls")
        InfoRow(
            title = business?.name ?: "No business",
            body = "Owner: ${business?.ownerName ?: "-"} | Phone: ${business?.phone ?: "-"}"
        )
        SectionHeader("Branches", "Active branch: ${activeBranch?.name ?: "-"}")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(branchName, { branchName = it }, Modifier.weight(1f), label = { Text("Branch name") }, singleLine = true)
            Button(onClick = {
                onAddBranch(branchName)
                branchName = ""
            }) { Text("Add") }
        }
        branches.forEach { branch ->
            InfoRow(
                title = branch.name,
                body = if (branch.id == activeBranch?.id) "Active branch" else branch.id,
                action = if (branch.id == activeBranch?.id) null else "Switch"
            ) {
                onSwitchBranch(branch.id)
            }
        }
        SectionHeader("Role accounts", "Current-session demo accounts")
        OutlinedTextField(employeeName, { employeeName = it }, Modifier.fillMaxWidth(), label = { Text("Employee name") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UserRole.entries.forEach { role ->
                Button(
                    onClick = { selectedRole = role },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedRole == role) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (selectedRole == role) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(role.displayName)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(employeePin, { employeePin = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("PIN") }, singleLine = true)
            Button(onClick = {
                onCreateUser(employeeName, selectedRole, employeePin)
                employeeName = ""
                employeePin = ""
                selectedRole = UserRole.Worker
            }) {
                Text("Create user")
            }
        }
        accounts.forEach { account ->
            InfoRow(account.name, "${account.role.displayName} | ${account.employeeId} | PIN ${account.pin}")
        }
    }
}

@Composable
private fun InventoryScreen(records: List<InventoryProductRecord>, movements: List<com.example.sipangwingwi.inventory.StockMovement>, selectedProductId: String, onSelectProduct: (String) -> Unit, onReceive: (String, String, String) -> Unit) {
    var quantity by remember { mutableStateOf("") }
    var supplierId by remember { mutableStateOf("supplier-nairobi-wholesale") }
    var reference by remember { mutableStateOf("") }

    ScreenColumn {
        SectionHeader("Inventory receiving", "Receive supplier stock and audit every movement")
        ProductChooser(records, selectedProductId, onSelectProduct)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit) }, modifier = Modifier.weight(1f), label = { Text("Quantity") }, singleLine = true)
            OutlinedTextField(reference, { reference = it }, modifier = Modifier.weight(1f), label = { Text("Reference") }, singleLine = true)
        }
        OutlinedTextField(supplierId, { supplierId = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Supplier ID") }, singleLine = true)
        Button(onClick = {
            onReceive(quantity, supplierId, reference)
            quantity = ""
            reference = ""
        }) { Text("Receive stock") }
        SectionHeader("Current stock", "Barcode and quantity on hand")
        records.forEach { ProductStockRow(it) }
        SectionHeader("Movement ledger", "Newest records first")
        movements.take(10).forEach {
            MovementRow(records.firstOrNull { record -> record.product.id == it.productId }?.product?.name ?: it.productId, it.quantityDelta, it.referenceId)
        }
    }
}

@Composable
private fun ProductsScreen(records: List<InventoryProductRecord>, onAdd: (String, String, String) -> Unit, onUpdate: (String, String, String, String) -> Unit) {
    var selectedId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf("") }
    var sku by remember { mutableStateOf("") }

    ScreenColumn {
        SectionHeader("Products", "Create products and edit names, SKU and barcode")
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Product name") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(barcode, { barcode = it }, Modifier.weight(1f), label = { Text("Barcode") }, singleLine = true)
            OutlinedTextField(sku, { sku = it }, Modifier.weight(1f), label = { Text("SKU") }, singleLine = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                onAdd(name, barcode, sku)
                name = ""
                barcode = ""
                sku = ""
                selectedId = ""
            }) { Text("Add product") }
            Button(onClick = { onUpdate(selectedId, name, barcode, sku) }, enabled = selectedId.isNotBlank()) { Text("Save edit") }
        }
        records.forEach { record ->
            InfoRow(record.product.name, "Barcode: ${record.product.barcode ?: "-"} | SKU: ${record.product.sku ?: "-"}", "Edit") {
                selectedId = record.product.id
                name = record.product.name
                barcode = record.product.barcode.orEmpty()
                sku = record.product.sku.orEmpty()
            }
        }
    }
}

@Composable
private fun SuppliersScreen(suppliers: List<SupplierRecord>, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    ScreenColumn {
        SectionHeader("Suppliers", "Create supplier records for receiving goods")
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Supplier name") }, singleLine = true)
        OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("Phone") }, singleLine = true)
        Button(onClick = {
            onAdd(name, phone)
            name = ""
            phone = ""
        }) { Text("Add supplier") }
        suppliers.forEach { InfoRow(it.name, "${it.id} | ${it.phone}") }
    }
}

@Composable
private fun PosScreen(records: List<InventoryProductRecord>, cart: List<CartLine>, onScan: (String) -> Unit, onAdd: (String) -> Unit, onClear: () -> Unit, onCashCheckout: () -> Unit) {
    var barcode by remember { mutableStateOf("") }
    ScreenColumn {
        SectionHeader("Point of sale", "Manual barcode entry now; camera scanner can plug in later")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(barcode, { barcode = it }, Modifier.weight(1f), label = { Text("Barcode") }, singleLine = true)
            Button(onClick = {
                onScan(barcode)
                barcode = ""
            }) { Text("Scan") }
        }
        SectionHeader("Products", "Tap to add to cart")
        records.forEach { record -> InfoRow(record.product.name, "${record.stock.quantityOnHand} units | ${record.product.barcode ?: "No barcode"}", "Add") { onAdd(record.product.id) } }
        SectionHeader("Cart", "${cart.sumOf { it.quantity }} units")
        cart.forEach { InfoRow(it.product.name, "Qty ${it.quantity}") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCashCheckout) { Text("Cash checkout") }
            TextButton(onClick = onClear) { Text("Clear") }
        }
    }
}

@Composable
private fun PaymentsScreen(cart: List<CartLine>, state: MpesaPaymentState, onStart: (String) -> Unit, onComplete: () -> Unit, onFail: () -> Unit) {
    var phone by remember { mutableStateOf("") }
    ScreenColumn {
        SectionHeader("M-Pesa", "Simulated STK flow; real Daraja calls remain backend-only")
        Text("Cart units: ${cart.sumOf { it.quantity }}")
        Text("Payment state: $state", color = when (state) {
            MpesaPaymentState.Completed -> SipangwingwiSuccess
            MpesaPaymentState.Failed -> SipangwingwiError
            MpesaPaymentState.Pending -> SipangwingwiInfo
            MpesaPaymentState.NotStarted -> MaterialTheme.colorScheme.onSurface
        })
        OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("Customer phone") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onStart(phone) }) { Text("Send STK") }
            Button(onClick = onComplete, enabled = state == MpesaPaymentState.Pending) { Text("Mark paid") }
            TextButton(onClick = onFail, enabled = state == MpesaPaymentState.Pending) { Text("Fail") }
        }
    }
}

@Composable
private fun ReceiptScreen(receipt: ReceiptPreview?) {
    ScreenColumn {
        SectionHeader("Receipt preview", "Thermal-printer-ready data preview")
        if (receipt == null) {
            EmptyState("No receipt yet. Complete a cash or M-Pesa checkout.")
        } else {
            Text("Sipangwingwi", fontWeight = FontWeight.Bold)
            Text("Receipt: ${receipt.receiptNumber}")
            Text("Cashier: ${receipt.employeeId}")
            Text("Payment: ${receipt.paymentMethod}")
            Spacer(Modifier.height(8.dp))
            receipt.lines.forEach { Text("${it.product.name} x ${it.quantity}") }
            Spacer(Modifier.height(8.dp))
            Text("Total units: ${receipt.totalUnits}", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StaffScreen(
    session: EmployeeSession?,
    summaries: List<EmployeeWorkSummary>,
    onLogin: (String) -> Unit,
    onOpenShift: () -> Unit,
    onCloseShift: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    ScreenColumn {
        SectionHeader("Staff and shift", "Cashier PIN and shift state")
        if (session == null) {
            OutlinedTextField(pin, { pin = it }, Modifier.fillMaxWidth(), label = { Text("Employee PIN") }, singleLine = true)
            Button(onClick = {
                onLogin(pin)
                pin = ""
            }) { Text("Login") }
            Text("Demo PIN: 1234", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("${session.employeeId} | ${session.role.displayName}", fontWeight = FontWeight.SemiBold)
            Text("Shift: ${if (session.shiftOpen) "Open" else "Closed"}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenShift, enabled = !session.shiftOpen) { Text("Open shift") }
                Button(onClick = onCloseShift, enabled = session.shiftOpen) { Text("Close shift") }
            }
            if (session.role == UserRole.Admin || session.role == UserRole.Manager) {
                SectionHeader("Employee overview", "Work done in the current session")
                summaries.forEach { summary ->
                    InfoRow(
                        title = "${summary.account.name} | ${summary.account.role.displayName}",
                        body = "Shift ${if (summary.shiftOpen) "open" else "closed"} | Receipts ${summary.receiptsHandled} | Units sold ${summary.unitsSold} | Movements ${summary.stockMovementsCreated}"
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductChooser(records: List<InventoryProductRecord>, selectedId: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        records.forEach { record ->
            val selected = record.product.id == selectedId
            TextButton(onClick = { onSelect(record.product.id) }) {
                Text(if (selected) "* ${record.product.name}" else record.product.name, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun ProductStockRow(record: InventoryProductRecord) {
    InfoRow(record.product.name, "${record.product.barcode ?: "No barcode"} | ${record.stock.quantityOnHand} units")
}

@Composable
private fun MovementRow(productName: String, quantityDelta: Long, referenceId: String?) {
    InfoRow(productName, "${if (quantityDelta > 0) "+" else ""}$quantityDelta | ${referenceId ?: "No reference"}")
}

@Composable
private fun InfoRow(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (action != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun SurfacePanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp), shadowElevation = 1.dp, content = content)
}

@Composable
private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
private fun MessageBanner(message: String?, isError: Boolean) {
    if (message == null) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background((if (isError) SipangwingwiError else SipangwingwiSuccess).copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Text(message, color = if (isError) SipangwingwiError else SipangwingwiSuccess, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmptyState(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = SipangwingwiInk)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
