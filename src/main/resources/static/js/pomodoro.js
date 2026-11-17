/**
 * Smart AI Library Management System - Pomodoro Study Timer
 * Works completely offline without requiring AI services.
 */

let pomodoroTimer = null;
let timeLeft = 25 * 60; // seconds
let initialDuration = 25 * 60;
let isRunning = false;
let currentMode = "FOCUS"; // FOCUS, SHORT_BREAK, LONG_BREAK
let completedSessionsCount = 0;

document.addEventListener("DOMContentLoaded", function () {
    const timerDisplay = document.getElementById("timer-display");
    const modeBadge = document.getElementById("timer-mode-badge");
    const startBtn = document.getElementById("btn-start");
    const pauseBtn = document.getElementById("btn-pause");
    const resetBtn = document.getElementById("btn-reset");
    const focusDurationInput = document.getElementById("focus-duration");
    const breakDurationInput = document.getElementById("break-duration");

    if (!timerDisplay) return; // not on timer page

    function updateDisplay() {
        const minutes = Math.floor(timeLeft / 60);
        const seconds = timeLeft % 60;
        timerDisplay.textContent = `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
        document.title = `${timerDisplay.textContent} - Pomodoro | Smart Library`;
    }

    function setMode(mode) {
        currentMode = mode;
        if (mode === "FOCUS") {
            const mins = focusDurationInput ? parseInt(focusDurationInput.value, 10) || 25 : 25;
            timeLeft = mins * 60;
            initialDuration = timeLeft;
            modeBadge.textContent = "Focus Session";
            modeBadge.className = "badge bg-primary px-3 py-2 fs-6";
        } else if (mode === "SHORT_BREAK") {
            const mins = breakDurationInput ? parseInt(breakDurationInput.value, 10) || 5 : 5;
            timeLeft = mins * 60;
            initialDuration = timeLeft;
            modeBadge.textContent = "Short Break";
            modeBadge.className = "badge bg-success px-3 py-2 fs-6";
        } else if (mode === "LONG_BREAK") {
            timeLeft = 15 * 60;
            initialDuration = timeLeft;
            modeBadge.textContent = "Long Rest";
            modeBadge.className = "badge bg-teal px-3 py-2 fs-6";
        }
        updateDisplay();
    }

    function startTimer() {
        if (isRunning) return;
        isRunning = true;
        startBtn.classList.add("d-none");
        pauseBtn.classList.remove("d-none");

        pomodoroTimer = setInterval(function () {
            if (timeLeft > 0) {
                timeLeft--;
                updateDisplay();
            } else {
                clearInterval(pomodoroTimer);
                isRunning = false;
                startBtn.classList.remove("d-none");
                pauseBtn.classList.add("d-none");
                handleSessionCompleted();
            }
        }, 1000);
    }

    function pauseTimer() {
        if (!isRunning) return;
        clearInterval(pomodoroTimer);
        isRunning = false;
        pauseBtn.classList.add("d-none");
        startBtn.classList.remove("d-none");
    }

    function resetTimer() {
        clearInterval(pomodoroTimer);
        isRunning = false;
        pauseBtn.classList.add("d-none");
        startBtn.classList.remove("d-none");
        setMode(currentMode);
    }

    async function handleSessionCompleted() {
        const completedMinutes = Math.round(initialDuration / 60);

        // Play chime or alert sound if browser allows
        try {
            const ctx = new (window.AudioContext || window.webkitAudioContext)();
            const osc = ctx.createOscillator();
            osc.type = "sine";
            osc.frequency.setValueAtTime(587.33, ctx.currentTime); // D5
            osc.connect(ctx.destination);
            osc.start();
            osc.stop(ctx.currentTime + 0.8);
        } catch (e) {}

        if (currentMode === "FOCUS") {
            completedSessionsCount++;
            const counterEl = document.getElementById("completed-counter");
            if (counterEl) counterEl.textContent = completedSessionsCount;

            showNotification(`Focus session complete (${completedMinutes} mins)! Logging study time...`, "success");

            // Persist to backend database
            try {
                const response = await fetchWithCsrf("/api/study-timer/session", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                        durationMinutes: completedMinutes,
                        sessionType: "FOCUS",
                        notes: "Completed Pomodoro study interval"
                    })
                });
                if (response.ok) {
                    showNotification("Study session recorded successfully in your academic profile!", "success");
                }
            } catch (err) {
                console.error("Failed to persist study session:", err);
            }

            // Auto-switch to break
            if (completedSessionsCount % 4 === 0) {
                setMode("LONG_BREAK");
            } else {
                setMode("SHORT_BREAK");
            }
        } else {
            showNotification("Break finished! Time to refocus.", "info");
            setMode("FOCUS");
        }
    }

    // Attach button listeners
    startBtn.addEventListener("click", startTimer);
    pauseBtn.addEventListener("click", pauseTimer);
    resetBtn.addEventListener("click", resetTimer);

    document.querySelectorAll(".mode-select-btn").forEach(btn => {
        btn.addEventListener("click", function () {
            pauseTimer();
            setMode(this.getAttribute("data-mode"));
        });
    });

    if (focusDurationInput) {
        focusDurationInput.addEventListener("change", function () {
            if (currentMode === "FOCUS" && !isRunning) setMode("FOCUS");
        });
    }
    if (breakDurationInput) {
        breakDurationInput.addEventListener("change", function () {
            if (currentMode === "SHORT_BREAK" && !isRunning) setMode("SHORT_BREAK");
        });
    }

    updateDisplay();
});
