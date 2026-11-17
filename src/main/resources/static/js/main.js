/**
 * Smart AI Library Management System - Main JavaScript Utilities
 */

document.addEventListener("DOMContentLoaded", function () {
    // Enable Bootstrap tooltips if any
    const tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'));
    tooltipTriggerList.map(function (tooltipTriggerEl) {
        return new bootstrap.Tooltip(tooltipTriggerEl);
    });

    // Auto-dismiss alert banners after 6 seconds
    setTimeout(function () {
        const alerts = document.querySelectorAll(".alert-dismissible");
        alerts.forEach(alert => {
            const bsAlert = new bootstrap.Alert(alert);
            bsAlert.close();
        });
    }, 6000);
});

/**
 * Returns CSRF token and header name from document meta tags
 */
function getCsrfInfo() {
    const tokenMeta = document.querySelector('meta[name="_csrf"]');
    const headerMeta = document.querySelector('meta[name="_csrf_header"]');
    return {
        token: tokenMeta ? tokenMeta.getAttribute("content") : "",
        header: headerMeta ? headerMeta.getAttribute("content") : "X-CSRF-TOKEN"
    };
}

/**
 * Standard fetch wrapper with automatic CSRF header inclusion
 */
async function fetchWithCsrf(url, options = {}) {
    const csrf = getCsrfInfo();
    const headers = options.headers || {};
    if (csrf.token) {
        headers[csrf.header] = csrf.token;
    }
    options.headers = headers;
    return fetch(url, options);
}

/**
 * Toast notification dispatcher
 */
function showNotification(message, type = "info") {
    let container = document.getElementById("toast-container");
    if (!container) {
        container = document.createElement("div");
        container.id = "toast-container";
        container.className = "toast-container position-fixed bottom-0 end-0 p-3";
        container.style.zIndex = "1090";
        document.body.appendChild(container);
    }

    const toastId = "toast-" + Date.now();
    const bgClass = type === "success" ? "bg-success text-white" :
                    type === "danger" ? "bg-danger text-white" :
                    type === "warning" ? "bg-warning text-dark" : "bg-primary text-white";

    const toastHtml = `
        <div id="${toastId}" class="toast align-items-center ${bgClass} border-0" role="alert" aria-live="assertive" aria-atomic="true">
            <div class="d-flex">
                <div class="toast-body">
                    ${message}
                </div>
                <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button>
            </div>
        </div>
    `;

    container.insertAdjacentHTML("beforeend", toastHtml);
    const toastEl = document.getElementById(toastId);
    const bsToast = new bootstrap.Toast(toastEl, { delay: 4500 });
    bsToast.show();

    toastEl.addEventListener("hidden.bs.toast", () => {
        toastEl.remove();
    });
}
