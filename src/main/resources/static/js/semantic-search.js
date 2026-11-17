/**
 * Smart AI Library Management System - Semantic Search
 */

document.addEventListener("DOMContentLoaded", function () {
    const searchForm = document.getElementById("semantic-search-form");
    const queryInput = document.getElementById("semantic-query-input");
    const resultsContainer = document.getElementById("semantic-results-container");
    const loadingSpinner = document.getElementById("semantic-loading");
    const emptyState = document.getElementById("semantic-empty");

    if (!searchForm || !resultsContainer) return;

    searchForm.addEventListener("submit", async function (e) {
        e.preventDefault();
        const query = queryInput.value.trim();
        if (!query) return;

        resultsContainer.innerHTML = "";
        if (emptyState) emptyState.classList.add("d-none");
        if (loadingSpinner) loadingSpinner.classList.remove("d-none");

        try {
            const response = await fetch(`/api/ai/semantic-search?query=${encodeURIComponent(query)}&topK=10`);
            if (loadingSpinner) loadingSpinner.classList.add("d-none");

            if (!response.ok) {
                showNotification("Failed to execute semantic search query.", "danger");
                return;
            }

            const results = await response.json();
            if (!results || results.length === 0) {
                if (emptyState) emptyState.classList.remove("d-none");
                return;
            }

            results.forEach(res => {
                const book = res.book;
                const scorePercent = Math.round(res.score * 100);
                const isVector = res.searchMode === "SEMANTIC_VECTOR";
                const modeBadge = isVector
                    ? `<span class="badge bg-primary me-2"><i class="bi bi-cpu me-1"></i> Vector Cosine (${scorePercent}%)</span>`
                    : `<span class="badge bg-secondary me-2"><i class="bi bi-search me-1"></i> Keyword Fallback</span>`;

                const isAvail = book.availableCopies > 0;
                const stockBadge = isAvail
                    ? `<span class="badge bg-success">${book.availableCopies} Copies Available</span>`
                    : `<span class="badge bg-danger">Out of Stock (0/${book.totalCopies})</span>`;

                const cardHtml = `
                    <div class="col-md-6 col-lg-4 mb-4">
                        <div class="card card-custom h-100">
                            <div class="card-body d-flex flex-column">
                                <div class="d-flex justify-content-between align-items-start mb-2">
                                    ${modeBadge}
                                    ${stockBadge}
                                </div>
                                <h5 class="card-title fw-bold text-dark mt-1">${escapeHtml(book.title)}</h5>
                                <div class="text-muted small mb-2"><i class="bi bi-person me-1"></i> ${escapeHtml(book.author)}</div>
                                <div class="small text-muted mb-2"><i class="bi bi-geo-alt me-1"></i> Shelf: <strong>${escapeHtml(book.locationShelf || 'General')}</strong></div>
                                <p class="card-text small text-secondary flex-grow-1" style="max-height: 80px; overflow: hidden; text-overflow: ellipsis;">
                                    ${escapeHtml(res.matchedSnippet || book.description || 'No description available.')}
                                </p>
                                <div class="pt-3 border-top mt-auto d-flex justify-content-between align-items-center">
                                    <span class="badge bg-light text-dark border">ISBN: ${escapeHtml(book.isbn)}</span>
                                    <a href="/student/books/${book.id}" class="btn btn-sm btn-outline-primary">View & Request</a>
                                </div>
                            </div>
                        </div>
                    </div>
                `;
                resultsContainer.insertAdjacentHTML("beforeend", cardHtml);
            });
        } catch (err) {
            if (loadingSpinner) loadingSpinner.classList.add("d-none");
            showNotification("Network error executing semantic search: " + err.message, "danger");
        }
    });

    function escapeHtml(str) {
        if (!str) return "";
        return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
    }
});
