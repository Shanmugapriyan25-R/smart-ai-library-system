/**
 * Smart AI Library Assistant - Chat Interface
 */

document.addEventListener("DOMContentLoaded", function () {
    const chatForm = document.getElementById("ai-chat-form");
    const queryInput = document.getElementById("ai-query-input");
    const messagesContainer = document.getElementById("ai-chat-messages");
    const sendBtn = document.getElementById("ai-send-btn");
    const statusPill = document.getElementById("ai-status-pill");

    if (!chatForm || !messagesContainer) return;

    // Fetch initial AI provider status
    fetchAiStatus();

    chatForm.addEventListener("submit", async function (e) {
        e.preventDefault();
        const query = queryInput.value.trim();
        if (!query) return;

        // Render user message
        appendMessage(query, "user");
        queryInput.value = "";
        queryInput.disabled = true;
        sendBtn.disabled = true;

        // Render loading indicator
        const loadingId = appendLoadingIndicator();

        try {
            const response = await fetchWithCsrf("/api/ai/chat", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ query: query, enableRag: true })
            });

            removeElement(loadingId);

            if (!response.ok) {
                const errData = await response.json().catch(() => ({}));
                appendMessage("An error occurred while querying the AI assistant: " + (errData.message || response.statusText), "ai");
                return;
            }

            const data = await response.json();
            renderAiResponse(data);
        } catch (err) {
            removeElement(loadingId);
            appendMessage("Network or connection error while contacting library assistant: " + err.message, "ai");
        } finally {
            queryInput.disabled = false;
            sendBtn.disabled = false;
            queryInput.focus();
        }
    });

    function appendMessage(text, sender) {
        const msgDiv = document.createElement("div");
        msgDiv.className = `chat-message ${sender}`;
        msgDiv.textContent = text;
        messagesContainer.appendChild(msgDiv);
        scrollToBottom();
        return msgDiv;
    }

    function appendLoadingIndicator() {
        const id = "loading-" + Date.now();
        const loadingDiv = document.createElement("div");
        loadingDiv.id = id;
        loadingDiv.className = "chat-message ai text-muted";
        loadingDiv.innerHTML = `<span class="spinner-border spinner-border-sm me-2 text-primary" role="status"></span>Analyzing catalogue, retrieving document chunks, and assembling grounded response...`;
        messagesContainer.appendChild(loadingDiv);
        scrollToBottom();
        return id;
    }

    function removeElement(id) {
        const el = document.getElementById(id);
        if (el) el.remove();
    }

    function renderAiResponse(data) {
        const container = document.createElement("div");
        container.className = "chat-message ai";

        // Provider status & RAG badge
        const metaDiv = document.createElement("div");
        metaDiv.className = "mb-2";
        const ragBadge = data.ragUsed
            ? `<span class="badge bg-primary-light text-primary me-2"><i class="bi bi-database-check me-1"></i> RAG Grounded</span>`
            : `<span class="badge bg-secondary text-white me-2"><i class="bi bi-info-circle me-1"></i> General Query</span>`;
        const providerBadge = data.fallbackMode
            ? `<span class="badge bg-light text-muted border"><i class="bi bi-cpu me-1"></i> ${escapeHtml(data.providerStatus)}</span>`
            : `<span class="badge bg-success-light text-success"><i class="bi bi-check2-circle me-1"></i> ${escapeHtml(data.providerStatus)}</span>`;
        metaDiv.innerHTML = ragBadge + providerBadge;
        container.appendChild(metaDiv);

        // Answer text (format line breaks)
        const textDiv = document.createElement("div");
        textDiv.style.whiteSpace = "pre-wrap";
        textDiv.textContent = data.answer;
        container.appendChild(textDiv);

        // Matched books with real database availability
        if (data.matchedBooks && data.matchedBooks.length > 0) {
            const booksHeader = document.createElement("div");
            booksHeader.className = "mt-3 pt-2 border-top fw-bold text-dark";
            booksHeader.innerHTML = `<i class="bi bi-book me-1 text-primary"></i> Matched Library Catalogue Records:`;
            container.appendChild(booksHeader);

            const bookList = document.createElement("div");
            bookList.className = "mt-2 d-flex flex-column gap-2";

            data.matchedBooks.forEach(b => {
                const bookCard = document.createElement("div");
                bookCard.className = "p-2 bg-white rounded border d-flex justify-content-between align-items-center";

                const isAvail = b.availableCopies > 0;
                const stockBadge = isAvail
                    ? `<span class="badge bg-success">${b.availableCopies} available in library</span>`
                    : `<span class="badge bg-danger">Currently out of stock (0/${b.totalCopies})</span>`;

                bookCard.innerHTML = `
                    <div>
                        <div class="fw-semibold text-dark">${escapeHtml(b.title)}</div>
                        <div class="small text-muted">Author: ${escapeHtml(b.author)} | Shelf: <span class="badge bg-light text-dark">${escapeHtml(b.locationShelf || 'Main')}</span></div>
                    </div>
                    <div class="text-end d-flex align-items-center gap-2">
                        ${stockBadge}
                        <a href="/student/books/${b.id}" class="btn btn-sm btn-outline-primary">View</a>
                    </div>
                `;
                bookList.appendChild(bookCard);
            });
            container.appendChild(bookList);
        }

        // Sources list
        if (data.sourceReferences && data.sourceReferences.length > 0) {
            const sourcesDiv = document.createElement("div");
            sourcesDiv.className = "mt-2 small text-muted";
            sourcesDiv.innerHTML = "<strong>Verified Sources:</strong> " + data.sourceReferences.map(s => escapeHtml(s)).join(" • ");
            container.appendChild(sourcesDiv);
        }

        messagesContainer.appendChild(container);
        scrollToBottom();
    }

    async function fetchAiStatus() {
        if (!statusPill) return;
        try {
            const resp = await fetch("/api/ai/status");
            if (resp.ok) {
                const status = await resp.json();
                statusPill.innerHTML = status.configured
                    ? `<span class="badge bg-success-light text-success"><i class="bi bi-cloud-check me-1"></i> ${escapeHtml(status.model)} Online</span>`
                    : `<span class="badge bg-warning-light text-warning"><i class="bi bi-hdd-network me-1"></i> Local Grounded RAG Search</span>`;
            }
        } catch (e) {}
    }

    function scrollToBottom() {
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }

    function escapeHtml(str) {
        if (!str) return "";
        return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
    }
});
