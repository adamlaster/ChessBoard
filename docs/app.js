/**
 * ChessBoard — Tabletop Web Prototype Application
 * Interactive simulator providing live launcher navigation, split-screen dual app multitasking,
 * 9 authentic tabletop UI themes, draggable divider, and tactical arrow drawing gestures.
 */

document.addEventListener('DOMContentLoaded', () => {

    const DEFAULT_DUOS = [
        { id: 'chess-youtube', name: 'Chess + YouTube', primary: 'chesscom', secondary: 'youtube', icon: '♟️▶️', subtitle: 'Play & Watch Stream', badge: 'DUO', ratio: 0.5 },
        { id: 'chesskids-youtube', name: 'ChessKids + YouTube', primary: 'chesskids', secondary: 'youtube', icon: '👑▶️', subtitle: 'Optimized 70/30 Split', badge: 'DUO 70/30', ratio: 0.7 },
        { id: 'chess-appletv', name: 'Chess + Apple TV', primary: 'chesscom', secondary: 'appletv', icon: '♟️📺', subtitle: 'Play & TV Streams', badge: 'DUO', ratio: 0.5 },
        { id: 'lichess-twitch', name: 'Lichess + Twitch', primary: 'lichess', secondary: 'twitch', icon: '♞🟣', subtitle: 'Analysis & Streamers', badge: 'DUO', ratio: 0.5 },
        { id: 'chess-reddit', name: 'Chess + Reddit', primary: 'chesscom', secondary: 'reddit', icon: '♟️👾', subtitle: 'r/chess Community', badge: 'DUO', ratio: 0.5 },
        { id: 'colonist-youtube', name: 'Colonist + YouTube', primary: 'colonist', secondary: 'youtube', icon: '🎲▶️', subtitle: 'Catan & Music', badge: 'DUO', ratio: 0.5 }
    ];

    function loadSavedDuos() {
        try {
            const saved = localStorage.getItem('chessboard_tabletop_duos');
            if (saved) {
                const parsed = JSON.parse(saved);
                if (Array.isArray(parsed) && parsed.length > 0) return parsed;
            }
        } catch (e) {}
        return [...DEFAULT_DUOS];
    }

    function saveDuosToStorage(duos) {
        try {
            localStorage.setItem('chessboard_tabletop_duos', JSON.stringify(duos));
        } catch (e) {}
    }

    // --- State ---
    const state = {
        theme: 'michigan',
        isSplit: false,
        splitRatio: 0.5,
        primaryApp: 'chesscom',
        secondaryApp: 'youtube',
        isControlsOpen: false,
        isLauncherOpen: true, // Start with launcher open so visitor immediately sees catalog
        rotationDeg: 0,
        activeAudio: 'left',
        leftPadMode: 0, // 0: None, 1: Outer, 2: All 4
        rightPadMode: 1,
        isSelectingSplit: false,
        duos: loadSavedDuos(),
        isEditingDuos: false
    };

    // --- App Catalog Mapping ---
    const APP_DATA = {
        chesscom: { title: 'Chess.com', icon: '♟️', img: 'screenshots/chesscom_analysis.png', isInteractive: true },
        lichess: { title: 'Lichess', icon: '♞', img: 'screenshots/lichess_analysis.png', isInteractive: false },
        chesskids: { title: 'ChessKids', icon: '👑', img: 'screenshots/chesskid_home.png', isInteractive: false },
        chessreps: { title: 'ChessReps', icon: '🎯', img: 'screenshots/chess_fullscreen.png', isInteractive: false },
        dj: { title: 'DJ Turntables', icon: '🎧', img: 'screenshots/dj_turntables.png', isInteractive: false },
        patatap: { title: 'Patatap', icon: '🎹', img: 'screenshots/dj_turntables.png', isInteractive: false },
        colonist: { title: 'Colonist.io', icon: '🐑', img: 'screenshots/chess_fullscreen.png', isInteractive: false },
        checkers: { title: '247 Checkers', icon: '🔴', img: 'screenshots/chess_fullscreen.png', isInteractive: false },
        backgammon: { title: '247 Backgammon', icon: '🎲', img: 'screenshots/chess_fullscreen.png', isInteractive: false },
        bga: { title: 'BoardGameArena', icon: '🌐', img: 'screenshots/chess_fullscreen.png', isInteractive: false },
        youtube: { title: 'YouTube', icon: '▶️', img: 'screenshots/split_duo_controls.png', isInteractive: false },
        twitch: { title: 'Twitch', icon: '🟣', img: 'screenshots/split_duo_controls.png', isInteractive: false },
        appletv: { title: 'Apple TV+', icon: '📺', img: 'screenshots/split_duo_controls.png', isInteractive: false },
        netflix: { title: 'Netflix', icon: '🍿', img: 'screenshots/split_duo_controls.png', isInteractive: false },
        disney: { title: 'Disney+', icon: '🏰', img: 'screenshots/split_duo_controls.png', isInteractive: false },
        reddit: { title: 'Reddit', icon: '👾', img: 'screenshots/chesscom_analysis.png', isInteractive: false }
    };

    // --- DOM Elements ---
    const hardwareBezel = document.getElementById('hardwareBezel');
    const boardHardwareBtn = document.getElementById('boardHardwareBtn');
    const appStage = document.getElementById('appStage');
    const primaryPane = document.getElementById('primaryPane');
    const secondaryPane = document.getElementById('secondaryPane');
    const splitDivider = document.getElementById('splitDivider');
    const dividerCurtain = document.getElementById('dividerCurtain');
    const curtainRatioLabel = document.getElementById('curtainRatioLabel');
    const topSplitBar = document.getElementById('topSplitBar');
    const browserControlsRail = document.getElementById('browserControlsRail');
    const launcherOverlay = document.getElementById('launcherOverlay');
    const themeModalOverlay = document.getElementById('themeModalOverlay');
    const guideModalOverlay = document.getElementById('guideModalOverlay');
    const boardOsOverlay = document.getElementById('boardOsOverlay');

    // Image backdrops
    const primaryStaticBackdrop = document.getElementById('primaryStaticBackdrop');
    const primaryStaticImg = document.getElementById('primaryStaticImg');
    const secondaryStaticImg = document.getElementById('secondaryStaticImg');
    const chessBoardView = document.getElementById('chessBoardView');

    // Header buttons
    const btnModeTabletop = document.getElementById('btnModeTabletop');
    const btnModeFullscreen = document.getElementById('btnModeFullscreen');
    const btnRotateBoard = document.getElementById('btnRotateBoard');
    const btnOpenGuide = document.getElementById('btnOpenGuide');

    // Split Bar buttons
    const btnAudioLeft = document.getElementById('btnAudioLeft');
    const btnAudioRight = document.getElementById('btnAudioRight');
    const btnSwapPanes = document.getElementById('btnSwapPanes');
    const btnSplitPreset = document.getElementById('btnSplitPreset');
    const btnMediaPlay = document.getElementById('btnMediaPlay');
    const btnPadLeft = document.getElementById('btnPadLeft');
    const btnPadRight = document.getElementById('btnPadRight');
    const btnHotSwapApp = document.getElementById('btnHotSwapApp');
    const btnInvert180 = document.getElementById('btnInvert180');
    const btnCloseSplit = document.getElementById('btnCloseSplit');

    // Rail Buttons
    const railCloseBtn = document.getElementById('railCloseBtn');
    const railLauncherBtn = document.getElementById('railLauncherBtn');
    const railSplitBtn = document.getElementById('railSplitBtn');
    const railRotateBtn = document.getElementById('railRotateBtn');
    const railMarginBtn = document.getElementById('railMarginBtn');
    const rightEdgeTrigger = document.getElementById('rightEdgeTrigger');
    const topEdgeTrigger = document.getElementById('topEdgeTrigger');

    // Launcher Header Buttons
    const hdrControlsBtn = document.getElementById('hdrControlsBtn');
    const hdrGuideBtn = document.getElementById('hdrGuideBtn');
    const hdrThemeBtn = document.getElementById('hdrThemeBtn');
    const launcherCloseBtn = document.getElementById('launcherCloseBtn');
    const launcherBackdrop = document.getElementById('launcherBackdrop');
    const clockDisplay = document.getElementById('clockDisplay');

    // Modal buttons
    const btnCloseTheme = document.getElementById('btnCloseTheme');
    const btnCloseGuide = document.getElementById('btnCloseGuide');
    const btnOsResume = document.getElementById('btnOsResume');
    const btnOsLauncher = document.getElementById('btnOsLauncher');
    const btnOsGuide = document.getElementById('btnOsGuide');
    const btnOsExit = document.getElementById('btnOsExit');

    // Duos Elements
    const duosCardTrack = document.getElementById('duosCardTrack');
    const duosCountBadge = document.getElementById('duosCountBadge');
    const duosEditBtn = document.getElementById('duosEditBtn');
    const addDuoModal = document.getElementById('addDuoModal');
    const leftPillTrack = document.getElementById('leftPillTrack');
    const rightPillTrack = document.getElementById('rightPillTrack');
    const leftUrlInput = document.getElementById('leftUrlInput');
    const rightUrlInput = document.getElementById('rightUrlInput');
    const duoNameInput = document.getElementById('duoNameInput');
    const duoIconInput = document.getElementById('duoIconInput');
    const btnCancelAddDuo = document.getElementById('btnCancelAddDuo');
    const btnConfirmAddDuo = document.getElementById('btnConfirmAddDuo');
    const btnResetDuos = document.getElementById('btnResetDuos');

    // --- Clock Updater ---
    function updateClock() {
        const now = new Date();
        clockDisplay.textContent = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }
    updateClock();
    setInterval(updateClock, 10000);

    // --- Tabletop Frame vs Fullscreen Toggle ---
    btnModeTabletop.addEventListener('click', () => {
        btnModeTabletop.classList.add('active');
        btnModeFullscreen.classList.remove('active');
        document.body.classList.remove('mode-fullscreen');
    });

    btnModeFullscreen.addEventListener('click', () => {
        btnModeFullscreen.classList.add('active');
        btnModeTabletop.classList.remove('active');
        document.body.classList.add('mode-fullscreen');
    });

    // --- Screen Rotation ---
    function rotateBoard() {
        state.rotationDeg = (state.rotationDeg + 90) % 360;
        if (state.rotationDeg === 90) {
            hardwareBezel.className = 'hardware-bezel rotated-90';
        } else if (state.rotationDeg === 180) {
            hardwareBezel.style.transform = 'rotate(180deg)';
        } else if (state.rotationDeg === 270) {
            hardwareBezel.style.transform = 'rotate(270deg) scale(0.68)';
        } else {
            hardwareBezel.className = 'hardware-bezel';
            hardwareBezel.style.transform = '';
        }
    }
    btnRotateBoard.addEventListener('click', rotateBoard);
    railRotateBtn.addEventListener('click', rotateBoard);

    // --- Launcher Open / Close ---
    function openLauncher(splitSelect = false) {
        state.isSelectingSplit = splitSelect;
        state.isLauncherOpen = true;
        launcherOverlay.classList.add('open');
    }

    function closeLauncher() {
        state.isLauncherOpen = false;
        launcherOverlay.classList.remove('open');
    }

    launcherCloseBtn.addEventListener('click', closeLauncher);
    launcherBackdrop.addEventListener('click', closeLauncher);
    railLauncherBtn.addEventListener('click', () => {
        closeControls();
        openLauncher();
    });
    topEdgeTrigger.addEventListener('click', () => openLauncher());

    // --- Browser Controls Rail Open / Close ---
    function openControls() {
        state.isControlsOpen = true;
        browserControlsRail.classList.add('open');
    }

    function closeControls() {
        state.isControlsOpen = false;
        browserControlsRail.classList.remove('open');
    }

    function toggleControls() {
        if (state.isControlsOpen) closeControls();
        else openControls();
    }

    railCloseBtn.addEventListener('click', closeControls);
    rightEdgeTrigger.addEventListener('click', toggleControls);
    hdrControlsBtn.addEventListener('click', () => {
        closeLauncher();
        openControls();
    });

    // --- App Launching ---
    function launchSingleApp(appId) {
        state.primaryApp = appId;
        closeSplitScreen();
        closeLauncher();

        const data = APP_DATA[appId] || APP_DATA.chesscom;
        if (data.isInteractive) {
            chessBoardView.style.display = 'flex';
            primaryStaticBackdrop.style.display = 'none';
        } else {
            chessBoardView.style.display = 'none';
            primaryStaticBackdrop.style.display = 'flex';
            primaryStaticImg.src = data.img;
        }

        // Highlight selected app in launcher
        document.querySelectorAll('.app-card').forEach(c => {
            if (c.dataset.app === appId) c.classList.add('active');
            else c.classList.remove('active');
        });
    }

    function launchDuo(primaryId, secondaryId, presetRatio = 0.5) {
        state.primaryApp = primaryId;
        state.secondaryApp = secondaryId;
        state.splitRatio = presetRatio;
        state.isSplit = true;
        closeLauncher();

        // Configure Primary Pane
        const primData = APP_DATA[primaryId] || APP_DATA.chesscom;
        if (primData.isInteractive) {
            chessBoardView.style.display = 'flex';
            primaryStaticBackdrop.style.display = 'none';
        } else {
            chessBoardView.style.display = 'none';
            primaryStaticBackdrop.style.display = 'flex';
            primaryStaticImg.src = primData.img;
        }

        // Configure Secondary Pane
        const secData = APP_DATA[secondaryId] || APP_DATA.youtube;
        secondaryStaticImg.src = secData.img;

        // Apply Split Layout
        appStage.classList.add('split-active');
        applySplitRatio(presetRatio);
        updateTopSplitBar();
    }

    function closeSplitScreen() {
        state.isSplit = false;
        appStage.classList.remove('split-active');
        primaryPane.style.flex = '1 1 100%';
        primaryPane.style.width = '100%';
        secondaryPane.style.display = 'none';
    }

    function applySplitRatio(ratio) {
        state.splitRatio = ratio;
        const leftPct = (ratio * 100).toFixed(1);
        const rightPct = ((1 - ratio) * 100).toFixed(1);

        primaryPane.style.flex = `0 0 ${leftPct}%`;
        primaryPane.style.width = `${leftPct}%`;
        secondaryPane.style.flex = `0 0 ${rightPct}%`;
        secondaryPane.style.width = `${rightPct}%`;
        splitDivider.style.left = `${leftPct}%`;

        btnSplitPreset.textContent = (ratio === 0.7) ? '◫ 70/30' : (ratio === 0.3) ? '◫ 30/70' : '◫ 50/50';
    }

    function updateTopSplitBar() {
        const primData = APP_DATA[state.primaryApp] || { title: 'Left' };
        const secData = APP_DATA[state.secondaryApp] || { title: 'Right' };

        btnAudioLeft.textContent = (state.activeAudio === 'left') ? `🔊 Left: ${primData.title}` : `🔇 Left: ${primData.title}`;
        btnAudioRight.textContent = (state.activeAudio === 'right') ? `🔊 Right: ${secData.title}` : `🔇 Right: ${secData.title}`;

        if (state.activeAudio === 'left') {
            btnAudioLeft.classList.add('active-audio');
            btnAudioRight.classList.remove('active-audio');
        } else {
            btnAudioRight.classList.add('active-audio');
            btnAudioLeft.classList.remove('active-audio');
        }
    }

    // App Card Clicks
    document.querySelectorAll('.app-card[data-app]').forEach(card => {
        card.addEventListener('click', () => {
            const appId = card.dataset.app;
            if (state.isSelectingSplit && state.isSplit) {
                state.secondaryApp = appId;
                const secData = APP_DATA[appId] || APP_DATA.youtube;
                secondaryStaticImg.src = secData.img;
                state.isSelectingSplit = false;
                closeLauncher();
                updateTopSplitBar();
            } else {
                launchSingleApp(appId);
            }
        });
    });

    // Helper to resolve APP_DATA key from URL or name
    function resolveAppIdFromUrlOrName(url, name, fallback) {
        const u = (url || '').toLowerCase();
        const n = (name || '').toLowerCase();
        if (u.includes('chess.com') || n.includes('chess.com') || n.includes('chess')) return 'chesscom';
        if (u.includes('lichess') || n.includes('lichess')) return 'lichess';
        if (u.includes('chesskid') || n.includes('chesskid') || n.includes('kids')) return 'chesskids';
        if (u.includes('chessreps') || n.includes('chessreps') || n.includes('reps')) return 'chessreps';
        if (u.includes('colonist') || n.includes('colonist') || n.includes('catan')) return 'colonist';
        if (u.includes('checkers') || n.includes('checkers')) return 'checkers';
        if (u.includes('backgammon') || n.includes('backgammon')) return 'backgammon';
        if (u.includes('boardgamearena') || n.includes('boardgamearena') || n.includes('bga')) return 'bga';
        if (u.includes('youtube') || n.includes('youtube')) return 'youtube';
        if (u.includes('apple') || n.includes('apple')) return 'appletv';
        if (u.includes('twitch') || n.includes('twitch')) return 'twitch';
        if (u.includes('reddit') || n.includes('reddit')) return 'reddit';
        if (u.includes('netflix') || n.includes('netflix')) return 'netflix';
        if (u.includes('disney') || n.includes('disney')) return 'disney';
        if (u.includes('dj') || u.includes('buttonbass') || n.includes('dj') || n.includes('turntable')) return 'dj';
        if (u.includes('patatap') || n.includes('patatap') || n.includes('synth')) return 'patatap';
        return fallback;
    }

    // --- Dynamic Tabletop Duos Management ---
    function renderDuos() {
        if (!duosCardTrack) return;
        duosCardTrack.innerHTML = '';
        if (duosCountBadge) {
            duosCountBadge.textContent = `${state.duos.length} ${state.duos.length === 1 ? 'duo' : 'duos'}`;
        }

        if (state.isEditingDuos) {
            duosCardTrack.classList.add('editing-duos');
        } else {
            duosCardTrack.classList.remove('editing-duos');
        }

        state.duos.forEach(duo => {
            const card = document.createElement('div');
            card.className = 'app-card';
            card.dataset.duo = duo.id;
            card.dataset.primary = duo.primary;
            card.dataset.secondary = duo.secondary;
            card.dataset.ratio = duo.ratio || 0.5;

            card.innerHTML = `
                <div class="app-icon">${duo.icon || '◫'}</div>
                <span class="card-badge duo-badge">${duo.badge || 'DUO'}</span>
                <button class="duo-delete-btn" data-id="${duo.id}" title="Remove Duo">✕</button>
                <h4>${duo.name}</h4>
                <p>${duo.subtitle || 'Tabletop Duo'}</p>
            `;

            card.addEventListener('click', (e) => {
                if (e.target.closest('.duo-delete-btn')) {
                    e.stopPropagation();
                    state.duos = state.duos.filter(d => d.id !== duo.id);
                    saveDuosToStorage(state.duos);
                    renderDuos();
                    return;
                }
                if (state.isEditingDuos) {
                    return; // In edit mode, clicking card does not launch
                }
                launchDuo(duo.primary || 'chesscom', duo.secondary || 'youtube', duo.ratio || 0.5);
            });

            duosCardTrack.appendChild(card);
        });

        // Add Duo Card (+ Add Duo)
        const addCard = document.createElement('div');
        addCard.className = 'app-card add-duo-card';
        addCard.innerHTML = `
            <div class="app-icon add-icon">➕</div>
            <h4>Add Duo</h4>
            <p>Create Custom Split</p>
        `;
        addCard.addEventListener('click', () => {
            openAddDuoModal();
        });
        duosCardTrack.appendChild(addCard);
    }

    if (duosEditBtn) {
        duosEditBtn.addEventListener('click', () => {
            state.isEditingDuos = !state.isEditingDuos;
            if (state.isEditingDuos) {
                duosEditBtn.textContent = '✓ Done';
                duosEditBtn.classList.add('active');
            } else {
                duosEditBtn.textContent = '✎ Edit';
                duosEditBtn.classList.remove('active');
            }
            renderDuos();
        });
    }

    // Modal helpers
    function openAddDuoModal() {
        if (!addDuoModal) return;
        addDuoModal.classList.add('open');
    }

    function closeAddDuoModal() {
        if (!addDuoModal) return;
        addDuoModal.classList.remove('open');
    }

    if (addDuoModal) {
        addDuoModal.addEventListener('click', (e) => {
            if (e.target === addDuoModal) closeAddDuoModal();
        });
    }

    if (btnCancelAddDuo) {
        btnCancelAddDuo.addEventListener('click', closeAddDuoModal);
    }

    let selectedLeftApp = { name: 'Chess', icon: '♟', url: 'https://www.chess.com', primaryId: 'chesscom' };
    let selectedRightApp = { name: 'YouTube', icon: '▶️', url: 'https://www.youtube.com', secondaryId: 'youtube' };

    function updateSuggestedDuoFields() {
        if (duoNameInput) {
            duoNameInput.value = `${selectedLeftApp.name} + ${selectedRightApp.name}`;
        }
        if (duoIconInput) {
            duoIconInput.value = `${selectedLeftApp.icon}${selectedRightApp.icon}`;
        }
    }

    if (leftPillTrack) {
        leftPillTrack.querySelectorAll('.duo-app-pill').forEach(pill => {
            pill.addEventListener('click', () => {
                leftPillTrack.querySelectorAll('.duo-app-pill').forEach(p => p.classList.remove('active'));
                pill.classList.add('active');
                selectedLeftApp = {
                    name: pill.dataset.name,
                    icon: pill.dataset.icon,
                    url: pill.dataset.url,
                    primaryId: resolveAppIdFromUrlOrName(pill.dataset.url, pill.dataset.name, 'chesscom')
                };
                if (leftUrlInput) leftUrlInput.value = pill.dataset.url;
                updateSuggestedDuoFields();
            });
        });
    }

    if (rightPillTrack) {
        rightPillTrack.querySelectorAll('.duo-app-pill').forEach(pill => {
            pill.addEventListener('click', () => {
                rightPillTrack.querySelectorAll('.duo-app-pill').forEach(p => p.classList.remove('active'));
                pill.classList.add('active');
                selectedRightApp = {
                    name: pill.dataset.name,
                    icon: pill.dataset.icon,
                    url: pill.dataset.url,
                    secondaryId: resolveAppIdFromUrlOrName(pill.dataset.url, pill.dataset.name, 'youtube')
                };
                if (rightUrlInput) rightUrlInput.value = pill.dataset.url;
                updateSuggestedDuoFields();
            });
        });
    }

    if (btnResetDuos) {
        btnResetDuos.addEventListener('click', () => {
            state.duos = [...DEFAULT_DUOS];
            saveDuosToStorage(state.duos);
            renderDuos();
            closeAddDuoModal();
        });
    }

    if (btnConfirmAddDuo) {
        btnConfirmAddDuo.addEventListener('click', () => {
            const name = (duoNameInput && duoNameInput.value.trim()) || `${selectedLeftApp.name} + ${selectedRightApp.name}`;
            const icon = (duoIconInput && duoIconInput.value.trim()) || `${selectedLeftApp.icon}${selectedRightApp.icon}`;
            const leftUrl = (leftUrlInput && leftUrlInput.value.trim()) || selectedLeftApp.url;
            const rightUrl = (rightUrlInput && rightUrlInput.value.trim()) || selectedRightApp.url;

            const primaryId = resolveAppIdFromUrlOrName(leftUrl, name, selectedLeftApp.primaryId || 'chesscom');
            const secondaryId = resolveAppIdFromUrlOrName(rightUrl, name, selectedRightApp.secondaryId || 'youtube');

            const newDuo = {
                id: 'duo-' + Date.now(),
                name: name,
                primary: primaryId,
                secondary: secondaryId,
                icon: icon,
                subtitle: `${selectedLeftApp.name} & ${selectedRightApp.name}`,
                badge: 'CUSTOM',
                ratio: 0.5
            };

            state.duos.push(newDuo);
            saveDuosToStorage(state.duos);
            renderDuos();
            closeAddDuoModal();
        });
    }

    // Initial Duos Render
    renderDuos();

    // --- Top Split Controls Bar Actions ---
    btnAudioLeft.addEventListener('click', () => {
        state.activeAudio = 'left';
        updateTopSplitBar();
    });

    btnAudioRight.addEventListener('click', () => {
        state.activeAudio = 'right';
        updateTopSplitBar();
    });

    btnSwapPanes.addEventListener('click', () => {
        const temp = state.primaryApp;
        state.primaryApp = state.secondaryApp;
        state.secondaryApp = temp;

        const primData = APP_DATA[state.primaryApp];
        const secData = APP_DATA[state.secondaryApp];

        if (primData.isInteractive) {
            chessBoardView.style.display = 'flex';
            primaryStaticBackdrop.style.display = 'none';
        } else {
            chessBoardView.style.display = 'none';
            primaryStaticBackdrop.style.display = 'flex';
            primaryStaticImg.src = primData.img;
        }
        secondaryStaticImg.src = secData.img;
        updateTopSplitBar();
    });

    btnSplitPreset.addEventListener('click', () => {
        if (state.splitRatio === 0.5) applySplitRatio(0.7);
        else if (state.splitRatio === 0.7) applySplitRatio(0.3);
        else applySplitRatio(0.5);
    });

    let isMediaPlaying = true;
    btnMediaPlay.addEventListener('click', () => {
        isMediaPlaying = !isMediaPlaying;
        btnMediaPlay.style.color = isMediaPlaying ? 'var(--accent)' : '#94A3B8';
    });

    btnPadLeft.addEventListener('click', () => {
        state.leftPadMode = (state.leftPadMode + 1) % 3;
        const labels = ['◧ L-Pad: Off', '◧ L-Pad: Outer', '◧ L-Pad: All 4'];
        btnPadLeft.textContent = labels[state.leftPadMode];
        primaryPane.style.padding = (state.leftPadMode === 1) ? '0 0 0 24px' : (state.leftPadMode === 2) ? '20px' : '0';
    });

    btnPadRight.addEventListener('click', () => {
        state.rightPadMode = (state.rightPadMode + 1) % 3;
        const labels = ['◨ R-Pad: Off', '◨ R-Pad: Right', '◨ R-Pad: All 4'];
        btnPadRight.textContent = labels[state.rightPadMode];
        secondaryPane.style.padding = (state.rightPadMode === 1) ? '0 32px 0 0' : (state.rightPadMode === 2) ? '20px' : '0';
    });

    btnHotSwapApp.addEventListener('click', () => {
        openLauncher(true);
    });

    let isSecondaryInverted = false;
    btnInvert180.addEventListener('click', () => {
        isSecondaryInverted = !isSecondaryInverted;
        secondaryPane.style.transform = isSecondaryInverted ? 'rotate(180deg)' : '';
        btnInvert180.style.color = isSecondaryInverted ? 'var(--accent)' : '#CBD5E1';
    });

    btnCloseSplit.addEventListener('click', closeSplitScreen);
    railSplitBtn.addEventListener('click', () => {
        if (state.isSplit) {
            closeSplitScreen();
        } else {
            launchDuo('chesscom', 'youtube', 0.5);
        }
    });

    // --- Draggable Center Divider ---
    let isDraggingDivider = false;

    splitDivider.addEventListener('mousedown', (e) => {
        isDraggingDivider = true;
        dividerCurtain.style.display = 'block';
        e.preventDefault();
    });

    window.addEventListener('mousemove', (e) => {
        if (!isDraggingDivider) return;
        const rect = appStage.getBoundingClientRect();
        let clientX = e.clientX;
        if (state.rotationDeg === 90 || state.rotationDeg === 270) {
            clientX = e.clientY;
        }

        const rawRatio = (clientX - rect.left) / rect.width;
        let clampedRatio = Math.max(0.20, Math.min(0.80, rawRatio));

        // Snapping near presets
        if (Math.abs(clampedRatio - 0.50) < 0.03) clampedRatio = 0.50;
        else if (Math.abs(clampedRatio - 0.70) < 0.03) clampedRatio = 0.70;
        else if (Math.abs(clampedRatio - 0.30) < 0.03) clampedRatio = 0.30;

        applySplitRatio(clampedRatio);
        curtainRatioLabel.textContent = `${Math.round(clampedRatio * 100)} / ${Math.round((1 - clampedRatio) * 100)}`;
    });

    window.addEventListener('mouseup', () => {
        if (isDraggingDivider) {
            isDraggingDivider = false;
            dividerCurtain.style.display = 'none';
        }
    });

    splitDivider.addEventListener('dblclick', () => {
        applySplitRatio(0.5);
    });

    // --- Tabletop Theme Picker ---
    const THEME_NAMES = {
        michigan: '〽️ Theme: Maize & Blue',
        pistons: '🏀 Theme: Pistons',
        lions: '🦁 Theme: Lions',
        tigers: '🐅 Theme: Tigers',
        unicorns: '🦄 Theme: Unicorns',
        walnut: '🪵 Theme: Walnut',
        emerald: '🟢 Theme: Emerald',
        carbon: '⚡ Theme: Carbon',
        classic: '♟️ Theme: Slate'
    };

    function applyTheme(themeId) {
        state.theme = themeId;
        document.body.className = `theme-${themeId}`;
        hdrThemeBtn.textContent = THEME_NAMES[themeId] || '🎨 Theme';

        document.querySelectorAll('.theme-option').forEach(opt => {
            if (opt.dataset.theme === themeId) opt.classList.add('active');
            else opt.classList.remove('active');
        });
    }

    hdrThemeBtn.addEventListener('click', () => {
        themeModalOverlay.classList.add('open');
    });

    btnCloseTheme.addEventListener('click', () => {
        themeModalOverlay.classList.remove('open');
    });

    themeModalOverlay.addEventListener('click', (e) => {
        if (e.target === themeModalOverlay) themeModalOverlay.classList.remove('open');
    });

    document.querySelectorAll('.theme-option').forEach(opt => {
        opt.addEventListener('click', () => {
            applyTheme(opt.dataset.theme);
            themeModalOverlay.classList.remove('open');
        });
    });

    // --- Controls Guide Modal ---
    function openGuide() {
        guideModalOverlay.classList.add('open');
    }
    function closeGuide() {
        guideModalOverlay.classList.remove('open');
    }

    btnOpenGuide.addEventListener('click', openGuide);
    hdrGuideBtn.addEventListener('click', openGuide);
    btnCloseGuide.addEventListener('click', closeGuide);
    guideModalOverlay.addEventListener('click', (e) => {
        if (e.target === guideModalOverlay) closeGuide();
    });

    // --- Board OS System Menu ---
    boardHardwareBtn.addEventListener('click', () => {
        boardOsOverlay.classList.add('open');
    });

    btnOsResume.addEventListener('click', () => {
        boardOsOverlay.classList.remove('open');
    });

    btnOsLauncher.addEventListener('click', () => {
        boardOsOverlay.classList.remove('open');
        openLauncher();
    });

    btnOsGuide.addEventListener('click', () => {
        boardOsOverlay.classList.remove('open');
        openGuide();
    });

    btnOsExit.addEventListener('click', () => {
        boardOsOverlay.classList.remove('open');
        alert('Simulating exit: returned to Board OS system library.');
    });

    boardOsOverlay.addEventListener('click', (e) => {
        if (e.target === boardOsOverlay) boardOsOverlay.classList.remove('open');
    });

    // --- Interactive Chessboard Initialization & Tactical Arrow Engine ---
    const chessGrid = document.getElementById('chessGrid');
    const arrowGroup = document.getElementById('arrowGroup');
    const interactiveBoard = document.getElementById('interactiveBoard');

    const INITIAL_BOARD = [
        ['♜','♞','♝','♛','♚','♝','♞','♜'],
        ['♟','♟','♟','♟','♟','♟','♟','♟'],
        ['','','','','','','',''],
        ['','','','','','','',''],
        ['','','','','','','',''],
        ['','','','','','','',''],
        ['♙','♙','♙','♙','♙','♙','♙','♙'],
        ['♖','♘','♗','♕','♔','♗','♘','♖']
    ];

    const files = ['a','b','c','d','e','f','g','h'];

    // Generate grid
    for (let r = 0; r < 8; r++) {
        for (let f = 0; f < 8; f++) {
            const sq = document.createElement('div');
            const isLight = (r + f) % 2 === 0;
            const sqName = `${files[f]}${8 - r}`;
            sq.className = `square ${isLight ? 'light' : 'dark'}`;
            sq.dataset.square = sqName;
            sq.dataset.file = f;
            sq.dataset.rank = r;

            // Labels
            if (r === 7) {
                const fl = document.createElement('span');
                fl.className = 'coord-label file-label';
                fl.textContent = files[f];
                sq.appendChild(fl);
            }
            if (f === 0) {
                const rl = document.createElement('span');
                rl.className = 'coord-label rank-label';
                rl.textContent = `${8 - r}`;
                sq.appendChild(rl);
            }

            const p = INITIAL_BOARD[r][f];
            if (p) {
                const pEl = document.createElement('span');
                pEl.className = `piece ${r < 2 ? 'black' : 'white'}`;
                pEl.textContent = p;
                sq.appendChild(pEl);
            }

            chessGrid.appendChild(sq);
        }
    }

    // --- Tactical Arrow & Highlight Gesture Implementation ---
    let tap1Time = 0;
    let tap1Square = null;
    let isDrawingArrow = false;
    let arrowStartSq = null;
    let previewArrow = null;

    function getSquareFromPoint(x, y) {
        const el = document.elementFromPoint(x, y);
        return el ? el.closest('.square') : null;
    }

    function getSquareCenterSvg(sq) {
        const boardRect = interactiveBoard.getBoundingClientRect();
        const sqRect = sq.getBoundingClientRect();
        const cx = ((sqRect.left + sqRect.width / 2) - boardRect.left) * (800 / boardRect.width);
        const cy = ((sqRect.top + sqRect.height / 2) - boardRect.top) * (800 / boardRect.height);
        return { x: cx, y: cy };
    }

    // Pointer events for double-tap gesture simulation
    interactiveBoard.addEventListener('pointerdown', (e) => {
        const sq = getSquareFromPoint(e.clientX, e.clientY);
        if (!sq) return;

        const now = Date.now();
        if (now - tap1Time < 320 && tap1Square === sq) {
            // Double-tap triggered!
            isDrawingArrow = true;
            arrowStartSq = sq;
            tap1Time = 0;
            tap1Square = null;

            // Create preview arrow
            const c = getSquareCenterSvg(sq);
            previewArrow = document.createElementNS('http://www.w3.org/2000/svg', 'line');
            previewArrow.setAttribute('class', 'tactical-arrow');
            previewArrow.setAttribute('marker-end', 'url(#arrowhead)');
            previewArrow.setAttribute('x1', c.x);
            previewArrow.setAttribute('y1', c.y);
            previewArrow.setAttribute('x2', c.x);
            previewArrow.setAttribute('y2', c.y);
            arrowGroup.appendChild(previewArrow);
            e.preventDefault();
        } else {
            tap1Time = now;
            tap1Square = sq;
        }
    });

    interactiveBoard.addEventListener('pointermove', (e) => {
        if (!isDrawingArrow || !previewArrow) return;
        const boardRect = interactiveBoard.getBoundingClientRect();
        const currentX = (e.clientX - boardRect.left) * (800 / boardRect.width);
        const currentY = (e.clientY - boardRect.top) * (800 / boardRect.height);
        previewArrow.setAttribute('x2', currentX);
        previewArrow.setAttribute('y2', currentY);
    });

    interactiveBoard.addEventListener('pointerup', (e) => {
        if (!isDrawingArrow) return;
        isDrawingArrow = false;

        const endSq = getSquareFromPoint(e.clientX, e.clientY);
        if (endSq && arrowStartSq && endSq !== arrowStartSq) {
            // Commit final arrow coordinates snapped to target square center
            const c2 = getSquareCenterSvg(endSq);
            previewArrow.setAttribute('x2', c2.x);
            previewArrow.setAttribute('y2', c2.y);
            previewArrow = null;
        } else if (arrowStartSq) {
            // Double-tap in place: toggle square color highlight!
            if (previewArrow) {
                previewArrow.remove();
                previewArrow = null;
            }
            arrowStartSq.classList.toggle('highlighted');
        }
    });

    // Single click clears markings
    interactiveBoard.addEventListener('click', (e) => {
        if (Date.now() - tap1Time > 320) {
            arrowGroup.innerHTML = '';
            document.querySelectorAll('.square.highlighted').forEach(s => s.classList.remove('highlighted'));
        }
    });

    // Pre-draw demonstration tactical arrows (e2 -> e4, g1 -> f3) and e4 highlight
    setTimeout(() => {
        const sqE2 = document.querySelector('.square[data-square="e2"]');
        const sqE4 = document.querySelector('.square[data-square="e4"]');
        const sqG1 = document.querySelector('.square[data-square="g1"]');
        const sqF3 = document.querySelector('.square[data-square="f3"]');

        if (sqE2 && sqE4) {
            const c1 = getSquareCenterSvg(sqE2);
            const c2 = getSquareCenterSvg(sqE4);
            const line1 = document.createElementNS('http://www.w3.org/2000/svg', 'line');
            line1.setAttribute('class', 'tactical-arrow');
            line1.setAttribute('marker-end', 'url(#arrowhead)');
            line1.setAttribute('x1', c1.x);
            line1.setAttribute('y1', c1.y);
            line1.setAttribute('x2', c2.x);
            line1.setAttribute('y2', c2.y);
            arrowGroup.appendChild(line1);
            sqE4.classList.add('highlighted');
        }

        if (sqG1 && sqF3) {
            const c3 = getSquareCenterSvg(sqG1);
            const c4 = getSquareCenterSvg(sqF3);
            const line2 = document.createElementNS('http://www.w3.org/2000/svg', 'line');
            line2.setAttribute('class', 'tactical-arrow');
            line2.setAttribute('marker-end', 'url(#arrowhead-gold)');
            line2.setAttribute('stroke', '#EAB308');
            line2.setAttribute('x1', c3.x);
            line2.setAttribute('y1', c3.y);
            line2.setAttribute('x2', c4.x);
            line2.setAttribute('y2', c4.y);
            arrowGroup.appendChild(line2);
        }
    }, 400);

    // Initial theme setup
    applyTheme('michigan');
});

