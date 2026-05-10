(() => {
    const STORAGE_KEY = "bloodinventory.dashboard.sidebarCollapsed";
    const THEME_STORAGE_KEY = "bloodinventory.dashboard.theme";
    const mobileQuery = window.matchMedia("(max-width: 920px)");
    const themeQuery = window.matchMedia("(prefers-color-scheme: dark)");

    function loadSystemPreferences() {
        if (document.querySelector("link[data-system-preferences]")) {
            return;
        }

        const link = document.createElement("link");
        link.rel = "stylesheet";
        link.href = "/css/system-preferences.css";
        link.dataset.systemPreferences = "true";
        document.head.appendChild(link);
    }

    function readStoredCollapsed() {
        try {
            return window.localStorage.getItem(STORAGE_KEY) === "true";
        } catch (error) {
            return false;
        }
    }

    loadSystemPreferences();

    function writeStoredCollapsed(collapsed) {
        try {
            window.localStorage.setItem(STORAGE_KEY, String(collapsed));
        } catch (error) {
            // Ignore storage failures and keep the UI working.
        }
    }

    function readStoredTheme() {
        try {
            const theme = window.localStorage.getItem(THEME_STORAGE_KEY);
            return theme === "dark" || theme === "light" ? theme : null;
        } catch (error) {
            return null;
        }
    }

    function writeStoredTheme(isDark) {
        try {
            window.localStorage.setItem(THEME_STORAGE_KEY, isDark ? "dark" : "light");
        } catch (error) {
            // Ignore storage failures and keep the UI working.
        }
    }

    function isDarkThemeActive() {
        const storedTheme = readStoredTheme();
        if (storedTheme !== null) {
            return storedTheme === "dark";
        }

        return themeQuery.matches;
    }

    function updateThemeButtons(isDark) {
        const nextLabel = isDark ? "Switch to light mode" : "Switch to dark mode";

        document.querySelectorAll("[data-theme-toggle]").forEach((button) => {
            if (!(button instanceof HTMLButtonElement)) {
                return;
            }

            button.classList.toggle("is-dark", isDark);
            button.setAttribute("aria-pressed", String(isDark));
            button.setAttribute("aria-label", nextLabel);
            button.title = nextLabel;

            const labelTarget = button.querySelector("[data-theme-toggle-label]");
            if (labelTarget instanceof HTMLElement) {
                labelTarget.textContent = nextLabel;
            }
        });
    }

    function applyTheme(isDark) {
        document.body.classList.toggle("theme-dark", isDark);
        updateThemeButtons(isDark);
    }

    function initThemeToggle() {
        applyTheme(isDarkThemeActive());

        document.querySelectorAll("[data-theme-toggle]").forEach((button) => {
            if (!(button instanceof HTMLButtonElement)) {
                return;
            }

            button.addEventListener("click", () => {
                const nextIsDark = !document.body.classList.contains("theme-dark");
                writeStoredTheme(nextIsDark);
                applyTheme(nextIsDark);
            });
        });
    }

    function updateButtons(app, expanded) {
        const nextLabel = mobileQuery.matches
            ? (expanded ? "Close menu" : "Open menu")
            : (expanded ? "Collapse menu" : "Expand menu");

        app.querySelectorAll("[data-sidebar-toggle]").forEach((button) => {
            button.setAttribute("aria-expanded", String(expanded));
            button.setAttribute("aria-label", nextLabel);

            const labelTarget = button.querySelector("[data-sidebar-toggle-label]");
            if (labelTarget) {
                labelTarget.textContent = nextLabel;
            }
        });
    }

    function syncState(app) {
        if (mobileQuery.matches) {
            updateButtons(app, app.classList.contains("sidebar-open"));
            return;
        }

        updateButtons(app, !app.classList.contains("sidebar-collapsed"));
    }

    function applySearch(app, query) {
        const normalizedQuery = query.trim().toLowerCase();

        app.querySelectorAll(".sidebar .nav-menu li").forEach((item) => {
            const matches = !normalizedQuery || item.textContent.toLowerCase().includes(normalizedQuery);
            item.hidden = !matches;
        });

        app.querySelectorAll("[data-search-item]").forEach((item) => {
            const matches = !normalizedQuery || item.textContent.toLowerCase().includes(normalizedQuery);
            item.hidden = !matches;
        });

        app.querySelectorAll(".sidebar .nav-group").forEach((group) => {
            const hasVisibleItems = Array.from(group.querySelectorAll(".nav-menu li")).some((item) => !item.hidden);
            group.hidden = !hasVisibleItems;
        });

        app.querySelectorAll(".sidebar .nav-section").forEach((section) => {
            const hasVisibleGroups = Array.from(section.querySelectorAll(".nav-group")).some((group) => !group.hidden);
            section.hidden = !hasVisibleGroups;
        });
    }

    function initSearch(app) {
        const searchForm = app.querySelector("[data-dashboard-search]");
        const searchInput = app.querySelector("[data-dashboard-search-input]");

        if (!(searchForm instanceof HTMLFormElement) || !(searchInput instanceof HTMLInputElement)) {
            return;
        }

        const updateSearch = () => applySearch(app, searchInput.value);

        searchInput.addEventListener("input", updateSearch);

        searchForm.addEventListener("submit", (event) => {
            event.preventDefault();
            updateSearch();
        });
    }

    function initProfileMenu(app) {
        const menus = app.querySelectorAll("[data-profile-menu]");

        if (!menus.length) {
            return;
        }

        const closeMenus = () => {
            menus.forEach((menu) => {
                menu.classList.remove("open");

                const toggle = menu.querySelector("[data-profile-toggle]");
                if (toggle instanceof HTMLElement) {
                    toggle.setAttribute("aria-expanded", "false");
                }
            });
        };

        menus.forEach((menu) => {
            const toggle = menu.querySelector("[data-profile-toggle]");
            const panel = menu.querySelector("[data-profile-panel]");

            if (!(toggle instanceof HTMLButtonElement) || !(panel instanceof HTMLElement)) {
                return;
            }

            toggle.addEventListener("click", (event) => {
                event.stopPropagation();
                const willOpen = !menu.classList.contains("open");
                closeMenus();
                menu.classList.toggle("open", willOpen);
                toggle.setAttribute("aria-expanded", String(willOpen));
            });

            panel.addEventListener("click", (event) => {
                event.stopPropagation();
            });
        });

        document.addEventListener("click", closeMenus);

        document.addEventListener("keydown", (event) => {
            if (event.key === "Escape") {
                closeMenus();
            }
        });
    }

    function initNotificationMenu(app) {
        const menus = app.querySelectorAll("[data-notification-menu]");

        if (!menus.length) {
            return;
        }

        const closeMenus = () => {
            menus.forEach((menu) => {
                menu.classList.remove("open");

                const toggle = menu.querySelector("[data-notification-toggle]");
                if (toggle instanceof HTMLElement) {
                    toggle.setAttribute("aria-expanded", "false");
                }
            });
        };

        menus.forEach((menu) => {
            const toggle = menu.querySelector("[data-notification-toggle]");
            const panel = menu.querySelector("[data-notification-panel]");

            if (!(toggle instanceof HTMLButtonElement) || !(panel instanceof HTMLElement)) {
                return;
            }

            toggle.addEventListener("click", (event) => {
                event.stopPropagation();
                const willOpen = !menu.classList.contains("open");
                closeMenus();
                menu.classList.toggle("open", willOpen);
                toggle.setAttribute("aria-expanded", String(willOpen));
            });

            panel.addEventListener("click", (event) => {
                event.stopPropagation();
            });
        });

        document.addEventListener("click", closeMenus);

        document.addEventListener("keydown", (event) => {
            if (event.key === "Escape") {
                closeMenus();
            }
        });
    }

    function initDashboardApp(app) {
        if (!(app instanceof HTMLElement)) {
            return;
        }

        if (!mobileQuery.matches && readStoredCollapsed()) {
            app.classList.add("sidebar-collapsed");
        }

        syncState(app);
        initSearch(app);
        initProfileMenu(app);
        initNotificationMenu(app);

        app.querySelectorAll("[data-sidebar-toggle]").forEach((button) => {
            button.addEventListener("click", () => {
                if (mobileQuery.matches) {
                    app.classList.toggle("sidebar-open");
                    syncState(app);
                    return;
                }

                const collapsed = app.classList.toggle("sidebar-collapsed");
                writeStoredCollapsed(collapsed);
                syncState(app);
            });
        });

        app.querySelectorAll("[data-sidebar-close]").forEach((button) => {
            button.addEventListener("click", () => {
                app.classList.remove("sidebar-open");
                syncState(app);
            });
        });
    }

    initThemeToggle();

    document.querySelectorAll(".dashboard-app").forEach(initDashboardApp);

    mobileQuery.addEventListener("change", (event) => {
        document.querySelectorAll(".dashboard-app").forEach((app) => {
            if (!(app instanceof HTMLElement)) {
                return;
            }

            if (event.matches) {
                app.classList.remove("sidebar-collapsed");
            } else {
                app.classList.remove("sidebar-open");
                app.classList.toggle("sidebar-collapsed", readStoredCollapsed());
            }

            syncState(app);
        });
    });

    themeQuery.addEventListener("change", (event) => {
        if (readStoredTheme() !== null) {
            return;
        }

        applyTheme(event.matches);
    });
})();
