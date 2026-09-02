((root) => {
    const normalize = (value) => String(value ?? "").trim().toLocaleLowerCase();

    const comparableValue = (value, type) => {
        if (type === "number") {
            const numericValue = Number(value);
            return Number.isFinite(numericValue) ? numericValue : 0;
        }

        if (type === "date") {
            const timestamp = Date.parse(value);
            return Number.isFinite(timestamp) ? timestamp : 0;
        }

        return normalize(value);
    };

    const filterAndSortRecords = (records, filters = [], sort = {}) => {
        const activeFilters = filters.filter((filter) => {
            const value = normalize(filter.value);
            return value && value !== "all";
        });

        const filteredRecords = records.filter((record) => activeFilters.every((filter) => {
            const recordValue = normalize(record.values?.[filter.key]);
            const requestedValue = normalize(filter.value);
            return filter.mode === "contains"
                ? recordValue.includes(requestedValue)
                : recordValue === requestedValue;
        }));

        if (!sort.key) {
            return filteredRecords;
        }

        const direction = sort.direction === "desc" ? -1 : 1;
        return filteredRecords.slice().sort((leftRecord, rightRecord) => {
            const leftValue = comparableValue(leftRecord.values?.[sort.key], sort.type);
            const rightValue = comparableValue(rightRecord.values?.[sort.key], sort.type);

            if (typeof leftValue === "string" && typeof rightValue === "string") {
                return leftValue.localeCompare(rightValue) * direction;
            }

            return (leftValue - rightValue) * direction;
        });
    };

    const api = { filterAndSortRecords };

    if (typeof module !== "undefined" && module.exports) {
        module.exports = api;
    }

    if (!root.document) {
        return;
    }

    root.document.querySelectorAll("[data-report-table]").forEach((reportTable) => {
        const form = reportTable.querySelector("[data-report-controls]");
        const tableBody = reportTable.querySelector("tbody");
        const rowElements = Array.from(reportTable.querySelectorAll("[data-report-row]"));
        const filteredEmptyRow = reportTable.querySelector("[data-report-filter-empty]");
        const resultCount = reportTable.querySelector("[data-report-count]");

        if (!form || !tableBody) {
            return;
        }

        const records = rowElements.map((element) => {
            const values = {};
            Array.from(element.attributes).forEach((attribute) => {
                const prefix = "data-report-value-";
                if (attribute.name.startsWith(prefix)) {
                    values[attribute.name.slice(prefix.length)] = attribute.value;
                }
            });
            return { element, values };
        });

        const applyControls = () => {
            const filters = Array.from(form.querySelectorAll("[data-report-filter]")).map((control) => ({
                key: control.getAttribute("data-report-filter"),
                value: control.value,
                mode: control.getAttribute("data-report-filter-mode") || "exact"
            }));
            const sortControl = form.querySelector("[data-report-sort]");
            const sortOption = sortControl?.options[sortControl.selectedIndex];
            const sortedRecords = filterAndSortRecords(records, filters, {
                key: sortOption?.getAttribute("data-sort-key") || "",
                type: sortOption?.getAttribute("data-sort-type") || "text",
                direction: sortOption?.getAttribute("data-sort-direction") || "asc"
            });
            const visibleElements = new Set(sortedRecords.map((record) => record.element));

            sortedRecords.forEach((record) => {
                record.element.hidden = false;
                tableBody.appendChild(record.element);
            });
            records
                .filter((record) => !visibleElements.has(record.element))
                .forEach((record) => {
                    record.element.hidden = true;
                    tableBody.appendChild(record.element);
                });

            if (filteredEmptyRow) {
                filteredEmptyRow.hidden = records.length === 0 || sortedRecords.length > 0;
                tableBody.appendChild(filteredEmptyRow);
            }

            if (resultCount) {
                resultCount.textContent = sortedRecords.length === records.length
                    ? `Showing all ${records.length} rows`
                    : `Showing ${sortedRecords.length} of ${records.length} rows`;
            }
        };

        form.addEventListener("submit", (event) => {
            event.preventDefault();
            applyControls();
        });

        form.querySelector("[data-report-reset]")?.addEventListener("click", () => {
            form.reset();
            applyControls();
        });

        applyControls();
    });
})(typeof globalThis !== "undefined" ? globalThis : window);
