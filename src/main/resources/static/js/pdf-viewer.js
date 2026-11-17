/**
 * Smart AI Library Management System - PDF.js Digital Document Reader
 */

let pdfDoc = null;
let pageNum = 1;
let pageRendering = false;
let pageNumPending = null;
let scale = 1.35;
let resourceId = null;

document.addEventListener("DOMContentLoaded", function () {
    const canvas = document.getElementById("pdf-canvas");
    if (!canvas) return;

    const ctx = canvas.getContext("2d");
    resourceId = canvas.getAttribute("data-resource-id");
    const initialPage = parseInt(canvas.getAttribute("data-initial-page"), 10) || 1;
    const pdfUrl = `/api/digital-reader/file/${resourceId}`;

    const prevBtn = document.getElementById("pdf-prev");
    const nextBtn = document.getElementById("pdf-next");
    const pageNumInput = document.getElementById("pdf-page-num");
    const pageCountSpan = document.getElementById("pdf-page-count");
    const zoomInBtn = document.getElementById("pdf-zoom-in");
    const zoomOutBtn = document.getElementById("pdf-zoom-out");
    const saveProgressBtn = document.getElementById("pdf-save-progress");

    // Configure PDF.js worker
    if (window.pdfjsLib) {
        pdfjsLib.GlobalWorkerOptions.workerSrc = "https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js";

        // Load document
        pdfjsLib.getDocument(pdfUrl).promise.then(function (pdfDoc_) {
            pdfDoc = pdfDoc_;
            if (pageCountSpan) pageCountSpan.textContent = pdfDoc.numPages;
            pageNum = Math.min(Math.max(1, initialPage), pdfDoc.numPages);
            if (pageNumInput) pageNumInput.value = pageNum;
            renderPage(pageNum);
        }).catch(function (error) {
            console.error("PDF load error:", error);
            const container = document.getElementById("pdf-render-container");
            if (container) {
                container.innerHTML = `
                    <div class="alert alert-danger my-4 p-4 text-center">
                        <i class="bi bi-exclamation-triangle-fill fs-2 d-block mb-2"></i>
                        <h5>Unable to Load Digital Document</h5>
                        <p class="mb-0 text-muted">Error: ${error.message || 'The requested PDF file is not available or access was denied.'}</p>
                    </div>
                `;
            }
        });
    }

    function renderPage(num) {
        pageRendering = true;
        pdfDoc.getPage(num).then(function (page) {
            const viewport = page.getViewport({ scale: scale });
            canvas.height = viewport.height;
            canvas.width = viewport.width;

            const renderContext = {
                canvasContext: ctx,
                viewport: viewport
            };
            const renderTask = page.render(renderContext);

            renderTask.promise.then(function () {
                pageRendering = false;
                if (pageNumPending !== null) {
                    renderPage(pageNumPending);
                    pageNumPending = null;
                }
            });
        });

        if (pageNumInput) pageNumInput.value = num;
        autoSaveProgress(num);
    }

    function queueRenderPage(num) {
        if (pageRendering) {
            pageNumPending = num;
        } else {
            renderPage(num);
        }
    }

    function onPrevPage() {
        if (pageNum <= 1) return;
        pageNum--;
        queueRenderPage(pageNum);
    }

    function onNextPage() {
        if (!pdfDoc || pageNum >= pdfDoc.numPages) return;
        pageNum++;
        queueRenderPage(pageNum);
    }

    if (prevBtn) prevBtn.addEventListener("click", onPrevPage);
    if (nextBtn) nextBtn.addEventListener("click", onNextPage);

    if (pageNumInput) {
        pageNumInput.addEventListener("change", function () {
            const val = parseInt(pageNumInput.value, 10);
            if (val >= 1 && val <= pdfDoc.numPages) {
                pageNum = val;
                queueRenderPage(pageNum);
            }
        });
    }

    if (zoomInBtn) {
        zoomInBtn.addEventListener("click", function () {
            scale += 0.2;
            queueRenderPage(pageNum);
        });
    }

    if (zoomOutBtn) {
        zoomOutBtn.addEventListener("click", function () {
            if (scale > 0.6) {
                scale -= 0.2;
                queueRenderPage(pageNum);
            }
        });
    }

    async function autoSaveProgress(page) {
        if (!resourceId || !pdfDoc) return;
        try {
            await fetchWithCsrf(`/api/digital-reader/progress?resourceId=${resourceId}&lastPage=${page}&totalPages=${pdfDoc.numPages}`, {
                method: "POST"
            });
        } catch (e) {}
    }

    if (saveProgressBtn) {
        saveProgressBtn.addEventListener("click", async function () {
            if (!resourceId || !pdfDoc) return;
            try {
                const resp = await fetchWithCsrf(`/api/digital-reader/progress?resourceId=${resourceId}&lastPage=${pageNum}&totalPages=${pdfDoc.numPages}`, {
                    method: "POST"
                });
                if (resp.ok) {
                    showNotification(`Reading progress saved at Page ${pageNum} (${Math.round(pageNum / pdfDoc.numPages * 100)}%)`, "success");
                }
            } catch (e) {
                showNotification("Could not save bookmark.", "danger");
            }
        });
    }
});
