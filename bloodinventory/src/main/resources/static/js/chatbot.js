(() => {
    const MAX_MODEL_HISTORY_MESSAGES = 10;
    const MAX_STORED_CONVERSATIONS = 12;
    const MAX_STORED_MESSAGES = 40;
    const MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
    const MAX_IMAGE_DATA_URL_LENGTH = 190000;
    const CHAT_STORAGE_KEY = "bloodInventory.chatbot.conversations.v1";
    const ACTIVE_CHAT_KEY = "bloodInventory.chatbot.activeConversationId.v1";
    const LAUNCHER_POSITION_KEY = "bloodInventory.chatbot.launcherPosition.v1";
    const LAUNCHER_MARGIN = 12;
    const DRAG_THRESHOLD = 6;
    const ALLOWED_AGENT_ROUTES = new Set([
        "/profile",
        "/admin/dashboard", "/admin/staff/management", "/admin/storage", "/admin/inventory",
        "/admin/reports", "/admin/audit", "/admin/deferral-rules", "/admin/settings",
        "/medical/dashboard", "/medical/donor-eligibility", "/medical/donations",
        "/medical/transfusion", "/medical/components",
        "/lab/dashboard", "/lab/pending-tests", "/lab/tti-screening",
        "/lab/component-status", "/lab/traceability"
    ]);
    const widget = document.querySelector("[data-chatbot-widget]");

    if (!(widget instanceof HTMLElement)) {
        return;
    }

    const toggle = widget.querySelector("[data-chatbot-toggle]");
    const closeButton = widget.querySelector("[data-chatbot-close]");
    const newChatButton = widget.querySelector("[data-chatbot-new-chat]");
    const historyToggleButton = widget.querySelector("[data-chatbot-history-toggle]");
    const historyPanel = widget.querySelector("[data-chatbot-history-panel]");
    const historyList = widget.querySelector("[data-chatbot-history-list]");
    const historyClearButton = widget.querySelector("[data-chatbot-history-clear]");
    const panel = widget.querySelector("[data-chatbot-panel]");
    const form = widget.querySelector("[data-chatbot-form]");
    const input = widget.querySelector("[data-chatbot-input]");
    const sendButton = widget.querySelector("[data-chatbot-send]");
    const attachButton = widget.querySelector("[data-chatbot-attach]");
    const imageInput = widget.querySelector("[data-chatbot-image-input]");
    const imagePreview = widget.querySelector("[data-chatbot-image-preview]");
    const imageThumbnail = widget.querySelector("[data-chatbot-image-thumbnail]");
    const imageName = widget.querySelector("[data-chatbot-image-name]");
    const imageRemoveButton = widget.querySelector("[data-chatbot-image-remove]");
    const messages = widget.querySelector("[data-chatbot-messages]");
    const status = widget.querySelector("[data-chatbot-status]");
    const modeStatus = widget.querySelector("[data-chatbot-mode-status]");
    const chatTab = widget.querySelector("[data-chatbot-chat-tab]");
    const modePicker = widget.querySelector("[data-chatbot-mode-picker]");
    const modeToggle = widget.querySelector("[data-chatbot-mode-toggle]");
    const modeMenu = widget.querySelector("[data-chatbot-mode-menu]");
    const modeLabel = widget.querySelector("[data-chatbot-mode-label]");
    const modeSummary = widget.querySelector("[data-chatbot-mode-summary]");
    const modeOptions = widget.querySelectorAll("[data-chatbot-mode-option]");
    const feedbackTab = widget.querySelector("[data-chatbot-feedback-tab]");
    const feedbackPanel = widget.querySelector("[data-chatbot-feedback-panel]");
    const feedbackForm = widget.querySelector("[data-chatbot-feedback-form]");
    const feedbackName = widget.querySelector("[data-chatbot-feedback-name]");
    const feedbackEmail = widget.querySelector("[data-chatbot-feedback-email]");
    const feedbackMessage = widget.querySelector("[data-chatbot-feedback-message]");
    const feedbackStatus = widget.querySelector("[data-chatbot-feedback-status]");
    const feedbackSubmitButton = widget.querySelector("[data-chatbot-feedback-submit]");
    const translate = window.BloodInventoryTranslate || ((value) => value);
    let selectedImage = null;
    let conversations = loadConversations();
    let activeConversationId = storageGet(ACTIVE_CHAT_KEY);
    let dragState = null;
    let suppressLauncherClick = false;
    let hasCustomLauncherPosition = false;

    if (!(toggle instanceof HTMLButtonElement)
            || !(closeButton instanceof HTMLButtonElement)
            || !(newChatButton instanceof HTMLButtonElement)
            || !(historyToggleButton instanceof HTMLButtonElement)
            || !(historyPanel instanceof HTMLElement)
            || !(historyList instanceof HTMLElement)
            || !(historyClearButton instanceof HTMLButtonElement)
            || !(panel instanceof HTMLElement)
            || !(form instanceof HTMLFormElement)
            || !(input instanceof HTMLTextAreaElement)
            || !(sendButton instanceof HTMLButtonElement)
            || !(attachButton instanceof HTMLButtonElement)
            || !(imageInput instanceof HTMLInputElement)
            || !(imagePreview instanceof HTMLElement)
            || !(imageThumbnail instanceof HTMLImageElement)
            || !(imageName instanceof HTMLElement)
            || !(imageRemoveButton instanceof HTMLButtonElement)
            || !(messages instanceof HTMLElement)
            || !(modeStatus instanceof HTMLElement)
            || !(chatTab instanceof HTMLButtonElement)
            || !(modePicker instanceof HTMLElement)
            || !(modeToggle instanceof HTMLButtonElement)
            || !(modeMenu instanceof HTMLElement)
            || !(modeLabel instanceof HTMLElement)
            || !(modeSummary instanceof HTMLElement)
            || modeOptions.length !== 2
            || !(feedbackTab instanceof HTMLButtonElement)
            || !(feedbackPanel instanceof HTMLElement)
            || !(feedbackForm instanceof HTMLFormElement)
            || !(feedbackName instanceof HTMLInputElement)
            || !(feedbackEmail instanceof HTMLInputElement)
            || !(feedbackMessage instanceof HTMLTextAreaElement)
            || !(feedbackStatus instanceof HTMLElement)
            || !(feedbackSubmitButton instanceof HTMLButtonElement)) {
        return;
    }

    function storageGet(key) {
        try {
            return window.localStorage.getItem(key);
        } catch (error) {
            return null;
        }
    }

    function storageSet(key, value) {
        try {
            window.localStorage.setItem(key, value);
        } catch (error) {
            return false;
        }

        return true;
    }

    function clamp(value, minimum, maximum) {
        return Math.min(Math.max(value, minimum), maximum);
    }

    function launcherBounds(left, top) {
        const launcherRect = toggle.getBoundingClientRect();
        const maximumLeft = Math.max(LAUNCHER_MARGIN, window.innerWidth - launcherRect.width - LAUNCHER_MARGIN);
        const maximumTop = Math.max(LAUNCHER_MARGIN, window.innerHeight - launcherRect.height - LAUNCHER_MARGIN);
        return {
            left: clamp(left, LAUNCHER_MARGIN, maximumLeft),
            top: clamp(top, LAUNCHER_MARGIN, maximumTop)
        };
    }

    function updatePanelPlacement() {
        if (panel.hidden) {
            return;
        }

        const launcherRect = toggle.getBoundingClientRect();
        const panelGap = 12;
        const availableAbove = Math.max(180, launcherRect.top - panelGap - LAUNCHER_MARGIN);
        const availableBelow = Math.max(180, window.innerHeight - launcherRect.bottom - panelGap - LAUNCHER_MARGIN);
        const placeBelow = availableBelow > availableAbove;
        const availableHeight = placeBelow ? availableBelow : availableAbove;

        widget.style.setProperty("--chatbot-panel-available-height", `${availableHeight}px`);
        panel.style.top = placeBelow ? `${launcherRect.height + panelGap}px` : "auto";
        panel.style.bottom = placeBelow ? "auto" : `${launcherRect.height + panelGap}px`;

        const panelWidth = panel.getBoundingClientRect().width;
        const maximumPanelLeft = Math.max(LAUNCHER_MARGIN, window.innerWidth - panelWidth - LAUNCHER_MARGIN);
        const viewportPanelLeft = clamp(
                launcherRect.right - panelWidth,
                LAUNCHER_MARGIN,
                maximumPanelLeft
        );
        panel.style.left = `${viewportPanelLeft - launcherRect.left}px`;
        panel.style.right = "auto";
    }

    function setLauncherPosition(left, top, persist = false) {
        const position = launcherBounds(left, top);
        widget.style.left = `${position.left}px`;
        widget.style.top = `${position.top}px`;
        widget.style.right = "auto";
        widget.style.bottom = "auto";
        hasCustomLauncherPosition = true;

        if (persist) {
            storageSet(LAUNCHER_POSITION_KEY, JSON.stringify(position));
        }

        updatePanelPlacement();
    }

    function restoreLauncherPosition() {
        const storedPosition = storageGet(LAUNCHER_POSITION_KEY);
        if (!storedPosition) {
            return;
        }

        try {
            const position = JSON.parse(storedPosition);
            if (Number.isFinite(position?.left) && Number.isFinite(position?.top)) {
                setLauncherPosition(position.left, position.top);
            }
        } catch (error) {
            // Ignore invalid saved positions and keep the default bottom-right location.
        }
    }

    function finishLauncherDrag(event, cancelled = false) {
        if (!dragState || event.pointerId !== dragState.pointerId) {
            return;
        }

        const moved = dragState.moved;
        dragState = null;
        widget.classList.remove("is-dragging");

        if (toggle.hasPointerCapture(event.pointerId)) {
            toggle.releasePointerCapture(event.pointerId);
        }

        if (!moved || cancelled) {
            suppressLauncherClick = false;
            return;
        }

        const widgetRect = widget.getBoundingClientRect();
        setLauncherPosition(widgetRect.left, widgetRect.top, true);
        suppressLauncherClick = true;
        window.setTimeout(() => {
            suppressLauncherClick = false;
        }, 0);
    }

    function moveLauncherWithKeyboard(event) {
        const direction = {
            ArrowUp: [0, -16],
            ArrowDown: [0, 16],
            ArrowLeft: [-16, 0],
            ArrowRight: [16, 0]
        }[event.key];

        if (!event.altKey || !direction) {
            return;
        }

        event.preventDefault();
        const launcherRect = widget.getBoundingClientRect();
        setLauncherPosition(
                launcherRect.left + direction[0],
                launcherRect.top + direction[1],
                true
        );
    }

    function createConversation(mode = "chat") {
        const now = new Date().toISOString();
        return {
            id: `chat-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
            title: "New chat",
            mode: mode === "agent" ? "agent" : "chat",
            createdAt: now,
            updatedAt: now,
            messages: []
        };
    }

    function normalizeConversation(value) {
        if (!value || typeof value !== "object" || !Array.isArray(value.messages)) {
            return null;
        }

        const id = typeof value.id === "string" && value.id ? value.id : createConversation().id;
        const createdAt = typeof value.createdAt === "string" ? value.createdAt : new Date().toISOString();
        const updatedAt = typeof value.updatedAt === "string" ? value.updatedAt : createdAt;
        const messagesValue = value.messages
                .map(normalizeMessage)
                .filter(Boolean)
                .slice(-MAX_STORED_MESSAGES);
        return {
            id,
            title: titleFromMessages(messagesValue),
            mode: value.mode === "agent" ? "agent" : "chat",
            createdAt,
            updatedAt,
            messages: messagesValue
        };
    }

    function normalizeMessage(value) {
        if (!value || typeof value !== "object") {
            return null;
        }

        const role = value.role === "user" ? "user" : "assistant";
        let content = String(value.content || "").trim();
        if (role === "assistant" && /^(failed to fetch|networkerror|load failed)$/i.test(content)) {
            content = translate("The previous request was interrupted. Please try it again.");
        }
        const hasImage = Boolean(value.hasImage);
        if (!content && !hasImage) {
            return null;
        }

        return {
            role,
            content,
            hasImage,
            mode: value.mode === "agent" ? "agent" : "chat"
        };
    }

    function loadConversations() {
        const raw = storageGet(CHAT_STORAGE_KEY);
        if (!raw) {
            return [];
        }

        try {
            const parsed = JSON.parse(raw);
            if (!Array.isArray(parsed)) {
                return [];
            }

            return parsed
                    .map(normalizeConversation)
                    .filter(Boolean)
                    .slice(0, MAX_STORED_CONVERSATIONS);
        } catch (error) {
            return [];
        }
    }

    function serializableConversation(conversation) {
        return {
            id: conversation.id,
            title: titleFromMessages(conversation.messages),
            mode: conversation.mode === "agent" ? "agent" : "chat",
            createdAt: conversation.createdAt,
            updatedAt: conversation.updatedAt,
            messages: conversation.messages
                    .slice(-MAX_STORED_MESSAGES)
                    .map((message) => ({
                        role: message.role,
                        content: message.content,
                        hasImage: Boolean(message.hasImage),
                        mode: message.mode === "agent" ? "agent" : "chat"
                    }))
        };
    }

    function saveConversations() {
        const ordered = conversations
                .slice()
                .sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt))
                .slice(0, MAX_STORED_CONVERSATIONS);
        conversations = ordered;
        storageSet(CHAT_STORAGE_KEY, JSON.stringify(ordered.map(serializableConversation)));
        storageSet(ACTIVE_CHAT_KEY, activeConversationId || "");
    }

    function activeConversation() {
        let conversation = conversations.find((item) => item.id === activeConversationId);
        if (!conversation) {
            conversation = createConversation();
            conversations.unshift(conversation);
            activeConversationId = conversation.id;
            saveConversations();
        }

        return conversation;
    }

    function ensureActiveConversation() {
        if (!conversations.length) {
            const conversation = createConversation();
            conversations.unshift(conversation);
            activeConversationId = conversation.id;
            saveConversations();
            return conversation;
        }

        const existing = conversations.find((conversation) => conversation.id === activeConversationId);
        if (existing) {
            return existing;
        }

        activeConversationId = conversations[0].id;
        saveConversations();
        return conversations[0];
    }

    function csrfHeaders() {
        const token = document.querySelector("meta[name='_csrf']")?.getAttribute("content");
        const header = document.querySelector("meta[name='_csrf_header']")?.getAttribute("content");

        if (!token || !header) {
            return {};
        }

        return { [header]: token };
    }

    function feedbackOpen() {
        return !feedbackPanel.hidden;
    }

    function currentMode() {
        return activeConversation().mode === "agent" ? "agent" : "chat";
    }

    function setModeMenuOpen(open) {
        modeMenu.hidden = !open;
        modeToggle.setAttribute("aria-expanded", String(open));
    }

    function updateModeUi() {
        const mode = currentMode();
        const agentActive = mode === "agent";
        chatTab.classList.toggle("is-active", !feedbackOpen());
        chatTab.setAttribute("aria-selected", String(!feedbackOpen()));
        modeStatus.textContent = translate(agentActive ? "Agent mode - Ready" : "Chat mode - Ready");
        input.placeholder = translate(agentActive ? "Describe a goal for the agent" : "Ask a question");
        modeLabel.textContent = translate(agentActive ? "Agent" : "Chat");
        modeSummary.textContent = translate(agentActive ? "Multi-step guidance" : "Quick answers");
        modeToggle.dataset.mode = mode;
        modeOptions.forEach((option) => {
            const selected = option.getAttribute("data-chatbot-mode-option") === mode;
            option.classList.toggle("is-selected", selected);
            option.setAttribute("aria-selected", String(selected));
        });
    }

    function setAssistantMode(mode) {
        setModeMenuOpen(false);
        setFeedbackOpen(false, false);
        const conversation = activeConversation();
        conversation.mode = mode === "agent" ? "agent" : "chat";
        conversation.updatedAt = new Date().toISOString();
        saveConversations();
        updateModeUi();
        renderConversation();

        if (widget.classList.contains("open")) {
            input.focus();
        }
    }

    function setOpen(open) {
        widget.classList.toggle("open", open);
        panel.hidden = !open;
        toggle.setAttribute("aria-expanded", String(open));
        if (!open) {
            setModeMenuOpen(false);
        }

        if (open) {
            updatePanelPlacement();
            if (feedbackOpen()) {
                feedbackName.focus();
            } else {
                input.focus();
            }
        }
    }

    function setFeedbackOpen(open, focus = true) {
        if (open) {
            setHistoryOpen(false);
            setModeMenuOpen(false);
        }

        feedbackPanel.hidden = !open;
        messages.hidden = open;
        form.hidden = open;
        feedbackTab.classList.toggle("is-active", open);
        feedbackTab.setAttribute("aria-selected", String(open));
        updateModeUi();

        if (!focus || !widget.classList.contains("open")) {
            return;
        }

        if (open) {
            feedbackName.focus();
            return;
        }

        input.focus();
    }

    function setHistoryOpen(open) {
        if (open) {
            setFeedbackOpen(false, false);
            setModeMenuOpen(false);
        }

        historyPanel.hidden = !open;
        historyToggleButton.setAttribute("aria-expanded", String(open));
        historyToggleButton.setAttribute("aria-label", open ? "Close chat history" : "Open chat history");

        if (open) {
            renderHistoryList();
        }
    }

    function setFeedbackStatus(message, state = "") {
        feedbackStatus.textContent = message ? translate(message) : "";

        if (state) {
            feedbackStatus.dataset.state = state;
            return;
        }

        delete feedbackStatus.dataset.state;
    }

    function setFeedbackBusy(busy) {
        feedbackName.disabled = busy;
        feedbackEmail.disabled = busy;
        feedbackMessage.disabled = busy;
        feedbackSubmitButton.disabled = busy;
        feedbackSubmitButton.setAttribute("aria-busy", String(busy));
    }

    function setBusy(busy) {
        widget.classList.toggle("is-busy", busy);
        input.disabled = busy;
        sendButton.disabled = busy;
        attachButton.disabled = busy;
        imageRemoveButton.disabled = busy;
        newChatButton.disabled = busy;
        historyToggleButton.disabled = busy;
        historyClearButton.disabled = busy;
        chatTab.disabled = busy;
        modeToggle.disabled = busy;
        modeOptions.forEach((option) => {
            option.disabled = busy;
        });
        feedbackTab.disabled = busy;

        if (status instanceof HTMLElement) {
            status.textContent = busy
                    ? translate(currentMode() === "agent" ? "Building a safe plan..." : "Thinking...")
                    : "";
        }
    }

    function setStatus(message) {
        if (status instanceof HTMLElement) {
            status.textContent = message ? translate(message) : "";
        }
    }

    function friendlyAssistantError(error) {
        const message = String(error?.message || "").trim();
        if (/failed to fetch|networkerror|load failed/i.test(message)) {
            return translate("Unable to reach the assistant. Refresh the page and try again.");
        }
        return message || translate("The assistant is unavailable right now.");
    }

    function renderConversation() {
        messages.innerHTML = "";
        const conversation = activeConversation();

        if (!conversation.messages.length) {
            renderEmptyState();
            return;
        }

        conversation.messages.forEach(renderMessage);
    }

    function renderEmptyState() {
        const agentMode = currentMode() === "agent";
        const empty = document.createElement("div");
        empty.className = "chatbot-empty-state";

        const copy = document.createElement("div");
        copy.className = "chatbot-empty-copy";
        const title = document.createElement("strong");
        title.textContent = translate(agentMode ? "What should we accomplish?" : "How can I help?");
        const description = document.createElement("span");
        description.textContent = translate(agentMode
                ? "I can create a safe plan, guide you to the right module, and keep you in control of every change."
                : "Ask about workflows, system features, or attach a screenshot for help.");
        copy.append(title, description);

        const suggestions = document.createElement("div");
        suggestions.className = "chatbot-suggestions";
        const prompts = suggestedPrompts(agentMode);

        prompts.forEach((prompt) => {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "chatbot-suggestion";
            button.textContent = translate(prompt);
            button.addEventListener("click", () => {
                input.value = translate(prompt);
                autoResize();
                input.focus();
            });
            suggestions.appendChild(button);
        });

        empty.append(copy, suggestions);
        messages.appendChild(empty);
    }

    function suggestedPrompts(agentMode) {
        const path = window.location.pathname.toLowerCase();
        if (path.startsWith("/admin/")) {
            return agentMode
                    ? [
                        "Review inventory risks and tell me what to check first",
                        "Help me audit recent system changes",
                        "Guide me through reviewing storage configuration"
                    ]
                    : [
                        "Summarize the administrator tools on this page",
                        "Where can I check near-expiry components?",
                        "How do I review inventory alerts?"
                    ];
        }
        if (path.startsWith("/medical/")) {
            return agentMode
                    ? [
                        "Guide me through processing a donation safely",
                        "Help me review donor eligibility",
                        "Plan a safe transfusion request review"
                    ]
                    : [
                        "Summarize the medical tools on this page",
                        "Explain the donor eligibility workflow",
                        "How do I review available components?"
                    ];
        }
        if (path.startsWith("/lab/")) {
            return agentMode
                    ? [
                        "Help me investigate a component traceability issue",
                        "Guide me through the pending test queue",
                        "Plan a TTI screening review"
                    ]
                    : [
                        "Summarize the laboratory tools on this page",
                        "Explain the component status workflow",
                        "How do I review pending tests?"
                    ];
        }
        return agentMode
                ? [
                    "Help me identify the correct module for this task",
                    "Plan a safe review of my assigned work",
                    "Explain what I can do with my current access"
                ]
                : [
                    "Summarize what I can do on this page",
                    "Explain my assigned system access",
                    "Help me find the correct module"
                ];
    }

    function routeAllowedForCurrentWorkspace(path) {
        const currentPath = window.location.pathname.toLowerCase();
        const normalizedPath = String(path || "").toLowerCase();
        if (normalizedPath === "/profile") {
            return true;
        }
        if (currentPath.startsWith("/admin/")) {
            return normalizedPath.startsWith("/admin/");
        }
        if (currentPath.startsWith("/medical/")) {
            return normalizedPath.startsWith("/medical/");
        }
        if (currentPath.startsWith("/lab/")) {
            return normalizedPath.startsWith("/lab/");
        }
        return false;
    }

    function responseParts(content) {
        const actions = [];
        let blockedCrossModuleAction = false;
        const cleaned = String(content || "").replace(
                /\[\[navigate:(\/[a-z0-9\-/]+)\|([^\]\n]{1,80})]]/gi,
                (token, path, label) => {
                    const normalizedPath = path.toLowerCase();
                    if (ALLOWED_AGENT_ROUTES.has(normalizedPath) && routeAllowedForCurrentWorkspace(normalizedPath)) {
                        actions.push({ path: normalizedPath, label: label.trim() });
                    } else if (ALLOWED_AGENT_ROUTES.has(normalizedPath)) {
                        blockedCrossModuleAction = true;
                    }
                    return "";
                }
        ).replace(/\n{3,}/g, "\n\n").trim();
        if (blockedCrossModuleAction) {
            return {
                text: translate("This saved response referenced a module outside your assigned role. Start a new request for guidance within your workspace."),
                actions: []
            };
        }
        return { text: cleaned, actions: actions.slice(0, 1) };
    }

    function renderMessage(message) {
        const item = document.createElement("div");
        item.className = `chatbot-message ${message.role === "user" ? "user" : "assistant"}`;
        if (message.role === "assistant" && message.mode === "agent") {
            item.classList.add("agent");
        }

        const bubble = document.createElement("div");
        bubble.className = "chatbot-bubble";

        if (message.role === "assistant" && message.mode === "agent") {
            const label = document.createElement("span");
            label.className = "chatbot-message-label";
            label.textContent = translate("Agent plan");
            bubble.appendChild(label);
        }

        if (message.imageDataUrl) {
            const image = document.createElement("img");
            image.className = "chatbot-message-image";
            image.src = message.imageDataUrl;
            image.alt = "";
            bubble.appendChild(image);
        } else if (message.hasImage) {
            const note = document.createElement("span");
            note.className = "chatbot-image-note";
            note.textContent = translate("Image attached");
            bubble.appendChild(note);
        }

        if (message.content) {
            const parts = message.role === "assistant"
                    ? responseParts(message.content)
                    : { text: message.content, actions: [] };
            const text = document.createElement("span");
            text.className = "chatbot-response-text";
            text.textContent = parts.text;
            bubble.appendChild(text);

            parts.actions.forEach((action) => {
                const link = document.createElement("a");
                link.className = "chatbot-navigation-action";
                link.href = action.path;
                link.textContent = action.label || translate("Open page");
                bubble.appendChild(link);
            });
        }

        item.appendChild(bubble);
        messages.appendChild(item);
        messages.scrollTop = messages.scrollHeight;
    }

    function addConversationMessage(role, content, options = {}) {
        const conversation = activeConversation();
        const message = {
            role,
            content: String(content || "").trim(),
            hasImage: Boolean(options.hasImage),
            mode: options.mode === "agent" ? "agent" : "chat"
        };

        if (options.imageDataUrl) {
            message.imageDataUrl = options.imageDataUrl;
        }

        conversation.messages.push(message);
        conversation.messages = conversation.messages.slice(-MAX_STORED_MESSAGES);
        conversation.title = titleFromMessages(conversation.messages);
        conversation.updatedAt = new Date().toISOString();
        saveConversations();
        renderMessage(message);
        renderHistoryList();
    }

    function titleFromMessages(messageList) {
        const userMessage = messageList.find((message) => message.role === "user" && (message.content || message.hasImage));
        if (!userMessage) {
            return "New chat";
        }

        const base = userMessage.content || "Image question";
        return truncate(base.replace(/\s+/g, " ").trim(), 42);
    }

    function modelHistorySnapshot() {
        return activeConversation().messages
                .filter((message) => message.content)
                .slice(-MAX_MODEL_HISTORY_MESSAGES)
                .map((message) => ({
                    role: message.role,
                    content: message.hasImage && message.role === "user"
                            ? `${message.content} [Image attached]`
                            : message.content
                }));
    }

    function formatDate(value) {
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return translate("Recently");
        }

        return new Intl.DateTimeFormat(undefined, {
            month: "short",
            day: "numeric",
            hour: "numeric",
            minute: "2-digit"
        }).format(date);
    }

    function renderHistoryList() {
        if (historyPanel.hidden) {
            return;
        }

        historyList.innerHTML = "";
        const saved = conversations
                .filter((conversation) => conversation.messages.length > 0)
                .sort((left, right) => Date.parse(right.updatedAt) - Date.parse(left.updatedAt));

        if (!saved.length) {
            const empty = document.createElement("div");
            empty.className = "chatbot-history-empty";
            empty.textContent = translate("No saved chats yet.");
            historyList.appendChild(empty);
            return;
        }

        saved.forEach((conversation) => {
            const item = document.createElement("div");
            item.className = "chatbot-history-item";

            const loadButton = document.createElement("button");
            loadButton.type = "button";
            loadButton.className = "chatbot-history-load";
            loadButton.setAttribute("aria-label", `Open chat: ${conversation.title}`);

            const title = document.createElement("strong");
            title.textContent = conversation.title;
            const meta = document.createElement("span");
            const modeLabel = conversation.mode === "agent" ? translate("Agent") : translate("Chat");
            meta.textContent = `${modeLabel} - ${formatDate(conversation.updatedAt)} - ${conversation.messages.length} messages`;
            loadButton.append(title, meta);

            const deleteButton = document.createElement("button");
            deleteButton.type = "button";
            deleteButton.className = "chatbot-icon-button chatbot-history-delete";
            deleteButton.setAttribute("aria-label", `Delete chat: ${conversation.title}`);
            deleteButton.title = "Delete";
            deleteButton.innerHTML = `
                <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M3 6h18"></path>
                    <path d="M8 6V4h8v2"></path>
                    <path d="m19 6-1 14H6L5 6"></path>
                </svg>
            `;

            loadButton.addEventListener("click", () => {
                activeConversationId = conversation.id;
                storageSet(ACTIVE_CHAT_KEY, activeConversationId);
                setHistoryOpen(false);
                updateModeUi();
                renderConversation();
                input.focus();
            });

            deleteButton.addEventListener("click", () => {
                deleteConversation(conversation.id);
            });

            item.append(loadButton, deleteButton);
            historyList.appendChild(item);
        });
    }

    function startNewChat() {
        setFeedbackOpen(false);
        const current = activeConversation();
        if (!current.messages.length) {
            setHistoryOpen(false);
            clearSelectedImage();
            renderConversation();
            input.focus();
            return;
        }

        const conversation = createConversation(currentMode());
        conversations.unshift(conversation);
        activeConversationId = conversation.id;
        saveConversations();
        setHistoryOpen(false);
        clearSelectedImage();
        renderConversation();
        input.value = "";
        autoResize();
        input.focus();
    }

    function deleteConversation(conversationId) {
        const preservedMode = currentMode();
        conversations = conversations.filter((conversation) => conversation.id !== conversationId);

        if (activeConversationId === conversationId) {
            const conversation = createConversation(preservedMode);
            conversations.unshift(conversation);
            activeConversationId = conversation.id;
            renderConversation();
        }

        if (!conversations.length) {
            const conversation = createConversation();
            conversations.unshift(conversation);
            activeConversationId = conversation.id;
        }

        saveConversations();
        renderHistoryList();
    }

    function clearHistory() {
        setFeedbackOpen(false);
        conversations = [createConversation(currentMode())];
        activeConversationId = conversations[0].id;
        saveConversations();
        setHistoryOpen(false);
        clearSelectedImage();
        input.value = "";
        autoResize();
        renderConversation();
        input.focus();
    }

    function autoResize() {
        input.style.height = "auto";
        input.style.height = `${Math.min(input.scrollHeight, 124)}px`;
    }

    function clearSelectedImage() {
        selectedImage = null;
        imageInput.value = "";
        imagePreview.hidden = true;
        imageThumbnail.removeAttribute("src");
        imageName.textContent = "";
    }

    function readAsDataUrl(file) {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();
            reader.addEventListener("load", () => resolve(String(reader.result || "")));
            reader.addEventListener("error", () => reject(new Error(translate("The image could not be read."))));
            reader.readAsDataURL(file);
        });
    }

    function loadImage(dataUrl) {
        return new Promise((resolve, reject) => {
            const image = new Image();
            image.addEventListener("load", () => resolve(image));
            image.addEventListener("error", () => reject(new Error(translate("The image could not be read."))));
            image.src = dataUrl;
        });
    }

    function renderCanvas(image, maxSide) {
        const scale = Math.min(1, maxSide / Math.max(image.naturalWidth, image.naturalHeight));
        const width = Math.max(1, Math.round(image.naturalWidth * scale));
        const height = Math.max(1, Math.round(image.naturalHeight * scale));
        const canvas = document.createElement("canvas");
        canvas.width = width;
        canvas.height = height;

        const context = canvas.getContext("2d");
        context.fillStyle = "#ffffff";
        context.fillRect(0, 0, width, height);
        context.drawImage(image, 0, 0, width, height);
        return canvas;
    }

    async function compressImage(file) {
        const source = await readAsDataUrl(file);
        const image = await loadImage(source);
        const maxSides = [1280, 960, 720, 560];
        const qualities = [0.82, 0.72, 0.62, 0.52, 0.44];

        for (const maxSide of maxSides) {
            const canvas = renderCanvas(image, maxSide);

            for (const quality of qualities) {
                const dataUrl = canvas.toDataURL("image/jpeg", quality);
                if (dataUrl.length <= MAX_IMAGE_DATA_URL_LENGTH) {
                    return {
                        dataUrl,
                        fileName: file.name || "uploaded-image.jpg",
                        mimeType: "image/jpeg"
                    };
                }
            }
        }

        throw new Error(translate("The image is too large. Please use a smaller image."));
    }

    function showSelectedImage(image) {
        selectedImage = image;
        imageThumbnail.src = image.dataUrl;
        imageName.textContent = image.fileName;
        imagePreview.hidden = false;
    }

    async function handleImageFile(file) {
        if (!file) {
            return;
        }

        if (!["image/png", "image/jpeg"].includes(file.type)) {
            setStatus("Please upload a PNG or JPG image.");
            return;
        }

        if (file.size > MAX_UPLOAD_BYTES) {
            setStatus("The image is too large. Please use a smaller image.");
            return;
        }

        setStatus("Preparing image...");

        try {
            showSelectedImage(await compressImage(file));
            setStatus("");
            setOpen(true);
            setHistoryOpen(false);
        } catch (error) {
            clearSelectedImage();
            setStatus(error.message || "The image could not be read.");
        }
    }

    async function ask(message, historySnapshot, image, mode) {
        const payload = {
            message,
            history: historySnapshot,
            pageTitle: document.title,
            pagePath: window.location.pathname,
            languageCode: currentInterfaceLanguage(),
            mode
        };

        if (image) {
            payload.imageDataUrl = image.dataUrl;
            payload.imageFileName = image.fileName;
            payload.imageMimeType = image.mimeType;
        }

        const response = await fetch("/api/chatbot/ask", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Accept": "application/json",
                ...csrfHeaders()
            },
            body: JSON.stringify(payload)
        });

        let body = null;
        try {
            body = await response.json();
        } catch (error) {
            body = null;
        }

        if (!response.ok || !body?.success) {
            throw new Error(body?.message || translate("The assistant is unavailable right now."));
        }

        return body.reply;
    }

    function currentInterfaceLanguage() {
        const language = String(
                document.body?.dataset.language
                || document.documentElement.lang
                || "en"
        ).trim().toLowerCase();

        if (language.startsWith("zh")) {
            return "zh";
        }
        return language === "ms" ? "ms" : "en";
    }

    async function submitFeedback(payload) {
        const response = await fetch("/api/chatbot/feedback", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Accept": "application/json",
                ...csrfHeaders()
            },
            body: JSON.stringify(payload)
        });

        let body = null;
        try {
            body = await response.json();
        } catch (error) {
            body = null;
        }

        if (!response.ok || !body?.success) {
            throw new Error(body?.message || translate("Unable to send feedback right now."));
        }

        return body.reply || body.message || "Feedback received. Thank you.";
    }

    function truncate(value, maxLength) {
        if (!value || value.length <= maxLength) {
            return value;
        }

        return `${value.slice(0, Math.max(1, maxLength - 3))}...`;
    }

    toggle.addEventListener("pointerdown", (event) => {
        if (event.button !== 0 || event.isPrimary === false) {
            return;
        }

        const launcherRect = widget.getBoundingClientRect();
        dragState = {
            pointerId: event.pointerId,
            startX: event.clientX,
            startY: event.clientY,
            startLeft: launcherRect.left,
            startTop: launcherRect.top,
            moved: false
        };
        toggle.setPointerCapture(event.pointerId);
    });

    toggle.addEventListener("pointermove", (event) => {
        if (!dragState || event.pointerId !== dragState.pointerId) {
            return;
        }

        const deltaX = event.clientX - dragState.startX;
        const deltaY = event.clientY - dragState.startY;
        if (!dragState.moved && Math.hypot(deltaX, deltaY) < DRAG_THRESHOLD) {
            return;
        }

        if (!dragState.moved) {
            dragState.moved = true;
            widget.classList.add("is-dragging");
            setOpen(false);
        }

        event.preventDefault();
        setLauncherPosition(
                dragState.startLeft + deltaX,
                dragState.startTop + deltaY
        );
    });

    toggle.addEventListener("pointerup", (event) => finishLauncherDrag(event));
    toggle.addEventListener("pointercancel", (event) => finishLauncherDrag(event, true));
    toggle.addEventListener("keydown", moveLauncherWithKeyboard);

    toggle.addEventListener("click", () => {
        if (suppressLauncherClick) {
            suppressLauncherClick = false;
            return;
        }

        setOpen(!widget.classList.contains("open"));
    });

    closeButton.addEventListener("click", () => setOpen(false));

    chatTab.addEventListener("click", () => {
        setOpen(true);
        setFeedbackOpen(false);
    });

    modeToggle.addEventListener("click", () => {
        setModeMenuOpen(modeMenu.hidden);
    });

    modeOptions.forEach((option) => {
        option.addEventListener("click", () => {
            setAssistantMode(option.getAttribute("data-chatbot-mode-option"));
        });
    });

    feedbackTab.addEventListener("click", () => {
        setOpen(true);
        setFeedbackOpen(true);
    });

    newChatButton.addEventListener("click", startNewChat);

    historyToggleButton.addEventListener("click", () => {
        setOpen(true);
        setHistoryOpen(historyPanel.hidden);
    });

    historyClearButton.addEventListener("click", clearHistory);

    document.addEventListener("keydown", (event) => {
        if (event.key !== "Escape" || !widget.classList.contains("open")) {
            return;
        }

        if (!modeMenu.hidden) {
            setModeMenuOpen(false);
            modeToggle.focus();
            return;
        }

        if (!historyPanel.hidden) {
            setHistoryOpen(false);
            return;
        }

        setOpen(false);
    });

    document.addEventListener("click", (event) => {
        if (!modeMenu.hidden && !modePicker.contains(event.target)) {
            setModeMenuOpen(false);
        }
    });

    window.addEventListener("resize", () => {
        if (hasCustomLauncherPosition) {
            const launcherRect = widget.getBoundingClientRect();
            setLauncherPosition(launcherRect.left, launcherRect.top);
            return;
        }

        updatePanelPlacement();
    });

    input.addEventListener("input", autoResize);

    attachButton.addEventListener("click", () => imageInput.click());

    imageInput.addEventListener("change", () => {
        handleImageFile(imageInput.files?.[0]);
    });

    imageRemoveButton.addEventListener("click", clearSelectedImage);

    feedbackForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        feedbackForm.classList.add("was-submitted");

        if (!feedbackForm.checkValidity()) {
            setFeedbackStatus("Please complete the feedback fields.", "error");
            return;
        }

        setFeedbackBusy(true);
        setFeedbackStatus("Sending feedback...");

        try {
            const reply = await submitFeedback({
                name: feedbackName.value.trim(),
                email: feedbackEmail.value.trim(),
                message: feedbackMessage.value.trim(),
                pageTitle: document.title,
                pagePath: window.location.pathname
            });
            feedbackForm.reset();
            feedbackForm.classList.remove("was-submitted");
            setFeedbackStatus(reply, "success");
        } catch (error) {
            setFeedbackStatus(error.message || "Unable to send feedback right now.", "error");
        } finally {
            setFeedbackBusy(false);
        }
    });

    input.addEventListener("keydown", (event) => {
        if (event.key === "Enter" && !event.shiftKey) {
            event.preventDefault();
            form.requestSubmit();
        }
    });

    form.addEventListener("submit", async (event) => {
        event.preventDefault();

        const imageToSend = selectedImage;
        const modeToSend = currentMode();
        const message = input.value.trim() || (imageToSend ? "Please analyze this image." : "");
        if (!message && !imageToSend) {
            return;
        }

        const historySnapshot = imageToSend ? [] : modelHistorySnapshot();
        setHistoryOpen(false);
        addConversationMessage("user", message, {
            hasImage: Boolean(imageToSend),
            imageDataUrl: imageToSend?.dataUrl,
            mode: modeToSend
        });
        input.value = "";
        clearSelectedImage();
        autoResize();
        setBusy(true);

        try {
            const reply = await ask(message, historySnapshot, imageToSend, modeToSend);
            addConversationMessage("assistant", reply, { mode: modeToSend });
        } catch (error) {
            addConversationMessage(
                    "assistant",
                    friendlyAssistantError(error),
                    { mode: modeToSend }
            );
        } finally {
            setBusy(false);
            input.focus();
        }
    });

    restoreLauncherPosition();
    ensureActiveConversation();
    updateModeUi();
    renderConversation();
    autoResize();
})();
