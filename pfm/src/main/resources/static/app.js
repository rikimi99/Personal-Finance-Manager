const API_URL = "/api/transactions";
const PAGE_SIZE = 8;
const CURRENCY = "EUR";
let csrfToken = "";
let authMode = "login";

const elements = {
    appShell: document.querySelector("#app-shell"),
    activityCount: document.querySelector("#activity-count"),
    amount: document.querySelector("#amount"),
    authCopy: document.querySelector("#auth-copy"),
    authEmail: document.querySelector("#auth-email"),
    authForm: document.querySelector("#auth-form"),
    authMessage: document.querySelector("#auth-message"),
    authPassword: document.querySelector("#auth-password"),
    authScreen: document.querySelector("#auth-screen"),
    authSubmit: document.querySelector("#auth-submit"),
    authSwitchButton: document.querySelector("#auth-switch-button"),
    authSwitchPrompt: document.querySelector("#auth-switch-prompt"),
    authTitle: document.querySelector("#auth-title"),
    balance: document.querySelector("#balance-value"),
    date: document.querySelector("#transaction-date"),
    description: document.querySelector("#description"),
    dialog: document.querySelector("#transaction-dialog"),
    dialogTitle: document.querySelector("#dialog-title"),
    emptyAdd: document.querySelector("#empty-add-button"),
    emptyState: document.querySelector("#empty-state"),
    expenses: document.querySelector("#expenses-value"),
    form: document.querySelector("#transaction-form"),
    formError: document.querySelector("#form-error"),
    income: document.querySelector("#income-value"),
    nextPage: document.querySelector("#next-page"),
    pageNumber: document.querySelector("#page-number"),
    pagination: document.querySelector("#pagination"),
    paginationCaption: document.querySelector("#pagination-caption"),
    previousPage: document.querySelector("#previous-page"),
    rows: document.querySelector("#transaction-rows"),
    sort: document.querySelector("#sort-select"),
    submit: document.querySelector("#submit-transaction"),
    signedInEmail: document.querySelector("#signed-in-email"),
    tableWrap: document.querySelector(".table-wrap"),
    today: document.querySelector("#today-label"),
    toastRegion: document.querySelector("#toast-region"),
    logout: document.querySelector("#logout-button")
};

const state = {
    page: 0,
    totalPages: 0,
    editingId: null,
    loading: false,
    reloadQueued: false
};

const moneyFormatter = new Intl.NumberFormat("en-IE", {
    style: "currency",
    currency: CURRENCY,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
});

const dateFormatter = new Intl.DateTimeFormat("en-IE", {
    day: "numeric",
    month: "short",
    year: "numeric"
});

function localDateValue() {
    const now = new Date();
    now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
    return now.toISOString().slice(0, 10);
}

function formatMoney(value) {
    return moneyFormatter.format(Number(value ?? 0));
}

function formatDate(value) {
    if (!value) return "—";
    return dateFormatter.format(new Date(`${value}T00:00:00`));
}

const AUTH_ENDPOINTS = ["/api/auth/login", "/api/auth/register", "/api/auth/me", "/api/auth/csrf"];

async function request(url, options = {}, retriedCsrf = false) {
    const method = (options.method ?? "GET").toUpperCase();
    const headers = {
        ...(options.body ? { "Content-Type": "application/json" } : {}),
        ...options.headers
    };
    if (!["GET", "HEAD", "OPTIONS"].includes(method) && csrfToken) {
        headers["X-XSRF-TOKEN"] = csrfToken;
    }

    const response = await fetch(url, {
        ...options,
        method,
        headers
    });

    if (response.status === 204) return null;

    // A stale CSRF token (e.g. the cookie was cleared) yields 403: refresh it and retry once.
    if (response.status === 403 && !retriedCsrf && !["GET", "HEAD", "OPTIONS"].includes(method)) {
        await loadCsrfToken();
        return request(url, options, true);
    }

    const contentType = response.headers.get("content-type") ?? "";
    const payload = contentType.includes("application/json")
        ? await response.json()
        : await response.text();

    if (!response.ok) {
        const validationMessages = payload?.errors
            ? Object.values(payload.errors).join(" ")
            : "";
        const error = new Error(
            validationMessages || payload?.message || `Request failed (${response.status}).`
        );
        error.status = response.status;
        if (response.status === 401 && !AUTH_ENDPOINTS.includes(url)) {
            handleSessionExpired();
        }
        throw error;
    }

    return payload;
}

async function loadCsrfToken() {
    const response = await fetch("/api/auth/csrf");
    if (!response.ok) {
        throw new Error(`Could not initialize secure session (${response.status}).`);
    }
    const payload = await response.json();
    csrfToken = payload.token;
}

function showAuthMessage(message, isError = true) {
    elements.authMessage.textContent = message;
    elements.authMessage.classList.toggle("is-error", isError);
    elements.authMessage.hidden = false;
}

function setAuthMode(mode) {
    authMode = mode;
    const registering = mode === "register";
    elements.authTitle.innerHTML = registering
        ? 'Create your account<span class="heading-period">.</span>'
        : 'Welcome back<span class="heading-period">.</span>';
    elements.authCopy.textContent = registering
        ? "Create a private ledger for your finances."
        : "Sign in to see your finances.";
    elements.authPassword.autocomplete = registering ? "new-password" : "current-password";
    elements.authPassword.minLength = registering ? 12 : 1;
    elements.authSubmit.textContent = registering ? "Create account" : "Sign in";
    elements.authSwitchPrompt.textContent = registering ? "Already have an account?" : "New to Ledger?";
    elements.authSwitchButton.textContent = registering ? "Sign in" : "Create an account";
    elements.authMessage.hidden = true;
}

function showDashboard(email) {
    elements.authScreen.hidden = true;
    elements.appShell.hidden = false;
    elements.signedInEmail.textContent = email;
}

function showSignIn() {
    if (elements.dialog.open) elements.dialog.close();
    state.page = 0;
    state.totalPages = 0;
    state.editingId = null;
    elements.signedInEmail.textContent = "";
    elements.rows.replaceChildren();
    elements.balance.textContent = "—";
    elements.income.textContent = "—";
    elements.expenses.textContent = "—";
    elements.appShell.hidden = true;
    elements.authScreen.hidden = false;
    elements.authPassword.value = "";
    setAuthMode("login");
    elements.authEmail.focus();
}

function handleSessionExpired() {
    if (elements.appShell.hidden) return;
    showSignIn();
    showAuthMessage("Your session has ended. Please sign in again.");
}

function showToast(message, isError = false) {
    const toast = document.createElement("div");
    toast.className = `toast${isError ? " is-error" : ""}`;
    toast.textContent = message;
    elements.toastRegion.append(toast);
    window.setTimeout(() => toast.remove(), 4200);
}

function renderSummary(summary) {
    elements.balance.textContent = formatMoney(summary.balance);
    elements.income.textContent = formatMoney(summary.totalIncome);
    elements.expenses.textContent = formatMoney(summary.totalExpenses);
}

function createIconButton(label, kind, iconPath) {
    const button = document.createElement("button");
    button.className = `icon-button${kind === "delete" ? " danger" : ""}`;
    button.type = "button";
    button.setAttribute("aria-label", label);
    button.title = label;
    button.innerHTML = `<svg viewBox="0 0 20 20" aria-hidden="true">${iconPath}</svg>`;
    return button;
}

function renderTransaction(transaction) {
    const isIncome = transaction.type === "INCOME";
    const row = document.createElement("tr");

    const nameCell = document.createElement("td");
    nameCell.className = "transaction-name";
    const monogram = document.createElement("span");
    monogram.className = `transaction-monogram${isIncome ? "" : " expense"}`;
    monogram.setAttribute("aria-hidden", "true");
    monogram.textContent = isIncome ? "+" : "−";
    const name = document.createElement("span");
    name.textContent = transaction.description?.trim() || "Untitled transaction";
    nameCell.append(monogram, name);

    const dateCell = document.createElement("td");
    dateCell.className = "transaction-date";
    dateCell.textContent = formatDate(transaction.transactionDate);

    const typeCell = document.createElement("td");
    const badge = document.createElement("span");
    badge.className = `type-badge${isIncome ? "" : " expense"}`;
    badge.textContent = isIncome ? "Income" : "Expense";
    typeCell.append(badge);

    const amountCell = document.createElement("td");
    amountCell.className = `amount-cell${isIncome ? "" : " expense"}`;
    amountCell.textContent = `${isIncome ? "+" : "−"}${formatMoney(transaction.amount)}`;

    const actionsCell = document.createElement("td");
    const actions = document.createElement("div");
    actions.className = "row-actions";
    const editButton = createIconButton(
        `Edit ${transaction.description || "transaction"}`,
        "edit",
        '<path d="m13.5 5.5 2 2M4 16l2.4-.5L16 5.9a1.4 1.4 0 0 0-2-2L4.4 13.5 4 16Z"/><path d="M11 17h5"/>'
    );
    editButton.addEventListener("click", () => openEditDialog(transaction));
    const deleteButton = createIconButton(
        `Delete ${transaction.description || "transaction"}`,
        "delete",
        '<path d="M4.5 6h11M8 6V4h4v2m2.5 0-.7 10H6.2L5.5 6m3 3v4m3-4v4"/>'
    );
    deleteButton.addEventListener("click", () => deleteTransaction(transaction));
    actions.append(editButton, deleteButton);
    actionsCell.append(actions);

    row.append(nameCell, dateCell, typeCell, amountCell, actionsCell);
    return row;
}

function renderTransactions(page) {
    elements.rows.replaceChildren();
    state.totalPages = page.totalPages ?? 0;

    const transactions = page.content ?? [];
    for (const transaction of transactions) {
        elements.rows.append(renderTransaction(transaction));
    }

    elements.tableWrap.hidden = transactions.length === 0;
    elements.emptyState.hidden = transactions.length > 0;
    elements.pagination.hidden = transactions.length === 0;
    elements.activityCount.textContent = page.totalElements === 1
        ? "One transaction in your ledger."
        : `${page.totalElements ?? 0} transactions in your ledger.`;

    const total = page.totalElements ?? 0;
    const firstItem = total === 0 ? 0 : page.number * page.size + 1;
    const lastItem = Math.min((page.number + 1) * page.size, total);
    elements.paginationCaption.textContent = `Showing ${firstItem}–${lastItem} of ${total}`;
    elements.pageNumber.textContent = `${(page.number ?? 0) + 1} / ${Math.max(page.totalPages ?? 0, 1)}`;
    elements.previousPage.disabled = page.first ?? true;
    elements.nextPage.disabled = page.last ?? true;
}

async function loadSummary() {
    try {
        const summary = await request(`${API_URL}/summary`);
        renderSummary(summary);
    } catch (error) {
        if (error.status === 401) return;
        elements.balance.textContent = "Unavailable";
        elements.income.textContent = "Unavailable";
        elements.expenses.textContent = "Unavailable";
        showToast(`Could not load your financial summary. ${error.message}`, true);
    }
}

async function loadTransactions() {
    if (state.loading) {
        // Don't drop a refresh requested mid-load (e.g. after a save); run it afterwards.
        state.reloadQueued = true;
        return;
    }
    state.loading = true;
    state.reloadQueued = false;
    let reloadLastPage = false;
    elements.sort.disabled = true;
    elements.previousPage.disabled = true;
    elements.nextPage.disabled = true;
    elements.tableWrap.hidden = false;
    elements.emptyState.hidden = true;
    elements.rows.innerHTML = '<tr><td class="table-message" colspan="5"><span class="loading-dots">Loading your transactions</span></td></tr>';

    const params = new URLSearchParams({
        page: String(state.page),
        size: String(PAGE_SIZE),
        sort: elements.sort.value
    });

    try {
        const page = await request(`${API_URL}?${params}`);
        if (state.page >= page.totalPages && page.totalPages > 0) {
            // Requested page no longer exists (e.g. rows deleted elsewhere): show the last one.
            state.page = page.totalPages - 1;
            reloadLastPage = true;
        } else {
            renderTransactions(page);
        }
    } catch (error) {
        if (error.status === 401) return;
        elements.tableWrap.hidden = false;
        elements.rows.innerHTML = `<tr><td class="table-message" colspan="5"></td></tr>`;
        elements.rows.querySelector(".table-message").textContent = `Could not load transactions. ${error.message}`;
        elements.pagination.hidden = true;
        elements.emptyState.hidden = true;
    } finally {
        state.loading = false;
        elements.sort.disabled = false;
        if (state.totalPages > 0) {
            elements.previousPage.disabled = state.page === 0;
            elements.nextPage.disabled = state.page + 1 >= state.totalPages;
        }
    }
    if ((reloadLastPage || state.reloadQueued) && !elements.appShell.hidden) {
        loadTransactions();
    }
}

async function refreshDashboard() {
    await Promise.all([loadSummary(), loadTransactions()]);
}

function resetForm() {
    elements.form.reset();
    elements.form.elements.type.value = "EXPENSE";
    elements.date.value = localDateValue();
    elements.formError.hidden = true;
    elements.formError.textContent = "";
    elements.submit.disabled = false;
    elements.submit.querySelector("span").textContent = "Save transaction";
}

function openCreateDialog() {
    state.editingId = null;
    resetForm();
    elements.dialogTitle.innerHTML = 'New transaction<span class="heading-period">.</span>';
    elements.dialog.showModal();
    elements.amount.focus();
}

function openEditDialog(transaction) {
    state.editingId = transaction.id;
    resetForm();
    elements.dialogTitle.innerHTML = 'Edit transaction<span class="heading-period">.</span>';
    elements.form.elements.type.value = transaction.type;
    elements.amount.value = transaction.amount;
    elements.description.value = transaction.description ?? "";
    elements.date.value = transaction.transactionDate;
    elements.submit.querySelector("span").textContent = "Save changes";
    elements.dialog.showModal();
    elements.amount.focus();
}

async function saveTransaction(event) {
    event.preventDefault();
    if (!elements.form.reportValidity()) return;

    const formData = new FormData(elements.form);
    const payload = {
        amount: Number(formData.get("amount")),
        type: formData.get("type"),
        description: formData.get("description").trim() || null,
        transactionDate: formData.get("transactionDate")
    };
    const isEditing = state.editingId !== null;
    const url = isEditing
        ? `${API_URL}/${state.editingId}`
        : API_URL;

    elements.formError.hidden = true;
    elements.submit.disabled = true;
    elements.submit.querySelector("span").textContent = isEditing ? "Saving…" : "Adding…";

    try {
        await request(url, {
            method: isEditing ? "PUT" : "POST",
            body: JSON.stringify(payload)
        });
        elements.dialog.close();
        // New transactions jump to the first page; edits keep the user where they were.
        if (!isEditing) state.page = 0;
        await refreshDashboard();
        showToast(isEditing ? "Transaction updated." : "Transaction added.");
    } catch (error) {
        if (error.status === 401) return;
        elements.formError.textContent = error.message;
        elements.formError.hidden = false;
        elements.submit.disabled = false;
        elements.submit.querySelector("span").textContent = isEditing ? "Save changes" : "Save transaction";
    }
}

async function deleteTransaction(transaction) {
    const label = transaction.description?.trim() || "this transaction";
    if (!window.confirm(`Delete “${label}”? This can’t be undone.`)) return;

    try {
        await request(`${API_URL}/${transaction.id}`, { method: "DELETE" });
        const visibleItems = document.querySelectorAll("#transaction-rows tr").length;
        if (visibleItems === 1 && state.page > 0) state.page -= 1;
        await refreshDashboard();
        showToast("Transaction deleted.");
    } catch (error) {
        if (error.status === 401) return;
        showToast(`Could not delete the transaction. ${error.message}`, true);
    }
}

async function submitAuthForm(event) {
    event.preventDefault();
    if (!elements.authForm.reportValidity()) return;

    elements.authSubmit.disabled = true;
    elements.authSubmit.textContent = authMode === "register"
        ? "Creating account…"
        : "Signing in…";
    elements.authMessage.hidden = true;
    const payload = {
        email: elements.authEmail.value.trim(),
        password: elements.authPassword.value
    };

    try {
        if (authMode === "register") {
            await request("/api/auth/register", {
                method: "POST",
                body: JSON.stringify(payload)
            });
            setAuthMode("login");
            elements.authEmail.value = payload.email;
            showAuthMessage("Account created. Sign in with your new credentials.", false);
            return;
        }

        const user = await request("/api/auth/login", {
            method: "POST",
            body: JSON.stringify(payload)
        });
        elements.authPassword.value = "";
        showDashboard(user.email);
        window.location.hash = "overview";
        await refreshDashboard();
    } catch (error) {
        showAuthMessage(error.message);
    } finally {
        elements.authSubmit.disabled = false;
        elements.authSubmit.textContent = authMode === "register" ? "Create account" : "Sign in";
    }
}

async function signOut() {
    try {
        await request("/api/auth/logout", { method: "POST" });
    } catch (error) {
        // 401 means the session had already ended, which is what signing out wants anyway.
        if (error.status !== 401) {
            showToast(`Could not sign out. ${error.message}`, true);
            return;
        }
    }
    csrfToken = "";
    showSignIn();
    try {
        await loadCsrfToken();
    } catch (error) {
        showAuthMessage(error.message);
    }
}

function setTodayLabel() {
    elements.today.textContent = new Intl.DateTimeFormat("en-IE", {
        weekday: "long",
        day: "numeric",
        month: "short"
    }).format(new Date());
}

const navigationLinks = [...document.querySelectorAll(".side-nav .nav-link")];

function activateNavigationLink(activeLink) {
    navigationLinks.forEach(link => {
        const isActive = link === activeLink;
        link.classList.toggle("is-active", isActive);
        if (isActive) {
            link.setAttribute("aria-current", "location");
        } else {
            link.removeAttribute("aria-current");
        }
    });
}

function navigateToSection(event) {
    const link = event.currentTarget;
    const target = document.querySelector(link.hash);
    if (!target) return;

    event.preventDefault();
    activateNavigationLink(link);
    if (window.location.hash !== link.hash) {
        window.history.pushState(null, "", link.hash);
    }
    target.scrollIntoView({
        behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches
            ? "auto"
            : "smooth",
        block: "start"
    });
}

navigationLinks.forEach(link => {
    link.addEventListener("click", navigateToSection);
});
window.addEventListener("popstate", () => {
    const activeLink = navigationLinks.find(link => link.hash === window.location.hash);
    if (activeLink) activateNavigationLink(activeLink);
});

document.querySelector("#add-transaction-button").addEventListener("click", openCreateDialog);
elements.authForm.addEventListener("submit", submitAuthForm);
elements.authSwitchButton.addEventListener("click", () => {
    setAuthMode(authMode === "login" ? "register" : "login");
});
elements.logout.addEventListener("click", signOut);
elements.emptyAdd.addEventListener("click", openCreateDialog);
document.querySelector("#dialog-close").addEventListener("click", () => elements.dialog.close());
document.querySelector("#dialog-cancel").addEventListener("click", () => elements.dialog.close());
elements.form.addEventListener("submit", saveTransaction);
elements.sort.addEventListener("change", () => {
    state.page = 0;
    loadTransactions();
});
elements.previousPage.addEventListener("click", () => {
    if (state.page > 0) {
        state.page -= 1;
        loadTransactions();
    }
});
elements.nextPage.addEventListener("click", () => {
    if (state.page + 1 < state.totalPages) {
        state.page += 1;
        loadTransactions();
    }
});
elements.dialog.addEventListener("click", (event) => {
    if (event.target === elements.dialog) elements.dialog.close();
});

setTodayLabel();
elements.date.value = localDateValue();
loadCsrfToken()
    .then(() => request("/api/auth/me"))
    .then(async user => {
        showDashboard(user.email);
        await refreshDashboard();
    })
    .catch(error => {
        showSignIn();
        if (error.status !== 401) {
            showAuthMessage(error.message);
        }
    });
