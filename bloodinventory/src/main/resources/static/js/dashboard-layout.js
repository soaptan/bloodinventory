(() => {
    const STORAGE_KEY = "bloodinventory.dashboard.sidebarCollapsed";
    const mobileQuery = window.matchMedia("(max-width: 920px)");

    function readStoredCollapsed() {
        try {
            return window.localStorage.getItem(STORAGE_KEY) === "true";
        } catch (error) {
            return false;
        }
    }

    function writeStoredCollapsed(collapsed) {
        try {
            window.localStorage.setItem(STORAGE_KEY, String(collapsed));
        } catch (error) {
            // Ignore storage failures and keep the UI working.
        }
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

    function initDashboardApp(app) {
        if (!(app instanceof HTMLElement)) {
            return;
        }

        if (!mobileQuery.matches && readStoredCollapsed()) {
            app.classList.add("sidebar-collapsed");
        }

        syncState(app);
        initSearch(app);

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
})();
