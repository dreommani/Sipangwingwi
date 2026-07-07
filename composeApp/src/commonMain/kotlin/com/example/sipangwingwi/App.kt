package com.example.sipangwingwi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.sipangwingwi.theme.SipangwingwiTheme
import kotlinx.coroutines.launch

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
                scope.launch {
                    show(store.completeInitialSetup(owner, phone, businessName, branchName, pin))
                }
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
            onLogin = { employeeId, pin ->
                scope.launch {
                    show(store.loginEmployee(employeeId, pin))
                }
            }
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
                        onAddBranch = { name -> scope.launch { show(store.addBranch(name)) } },
                        onSwitchBranch = { branchId -> scope.launch { show(store.switchBranch(branchId)) } },
                        onCreateUser = { name, role, pin -> scope.launch { show(store.createUser(name, role, pin)) } }
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
                        onLogin = { pin -> scope.launch { show(store.loginEmployee(pin)) } },
                        onOpenShift = { show(store.openShift()) },
                        onCloseShift = { show(store.closeShift()) }
                    )
                }
            }
        }
    }
}
