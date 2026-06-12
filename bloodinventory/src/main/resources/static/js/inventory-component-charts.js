(() => {
    const statusChart = document.querySelector("[data-status-chart]");
    const expiryChart = document.querySelector("[data-expiry-chart]");
    const statusLegend = document.querySelector("[data-status-legend]");
    const expiryLegend = document.querySelector("[data-expiry-legend]");
    const statusComponentFilter = document.querySelector("[data-status-component-filter]");
    const statusSort = document.querySelector("[data-status-sort]");
    const expiryStatusFilter = document.querySelector("[data-expiry-status-filter]");
    const expirySort = document.querySelector("[data-expiry-sort]");
    const statusApply = document.querySelector("[data-status-apply]");
    const expiryApply = document.querySelector("[data-expiry-apply]");

    if (!statusChart && !expiryChart) {
        return;
    }

    const statusRows = Array.from(document.querySelectorAll("[data-status-row]")).map((row) => ({
        componentType: row.getAttribute("data-component-type") || "Unknown",
        status: (row.getAttribute("data-status") || "Unknown").toUpperCase(),
        value: Number(row.getAttribute("data-total-units") || "0")
    }));
    const expiryRows = Array.from(document.querySelectorAll("[data-expiry-row]")).map((row) => ({
        expiryDate: row.getAttribute("data-expiry-date") || "",
        status: (row.getAttribute("data-status") || "Unknown").toUpperCase(),
        value: Number(row.getAttribute("data-total-units") || "0")
    }));
    const statusColors = {
        AVAILABLE: "#5bc784",
        USED: "#386bbc",
        RESERVED: "#f4ae3f",
        EXPIRED: "#ff667d",
        DISCARDED: "#94a3b8",
        QUARANTINED: "#8b6ff4"
    };
    const statusOrder = ["AVAILABLE", "USED", "QUARANTINED", "RESERVED", "DISCARDED", "EXPIRED"];
    const fallbackColors = ["#3e8cff", "#14b8d5", "#5bc784", "#f4ae3f", "#8b6ff4", "#ff667d"];

    const createSvgElement = (tag, attributes) => {
        const element = document.createElementNS("http://www.w3.org/2000/svg", tag);
        Object.entries(attributes || {}).forEach(([name, value]) => element.setAttribute(name, value));
        return element;
    };

    const colorForStatus = (status, index = 0) => statusColors[status] || fallbackColors[index % fallbackColors.length];

    const compareByStatusOrder = (left, right) => {
        const leftIndex = statusOrder.indexOf(left);
        const rightIndex = statusOrder.indexOf(right);

        if (leftIndex === -1 && rightIndex === -1) {
            return left.localeCompare(right);
        }

        if (leftIndex === -1) {
            return 1;
        }

        if (rightIndex === -1) {
            return -1;
        }

        return leftIndex - rightIndex;
    };

    const formatDate = (rawDate) => {
        if (!rawDate) {
            return "";
        }

        return new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric" })
            .format(new Date(`${rawDate}T00:00:00`));
    };

    const renderLegend = (target, entries) => {
        if (!target) {
            return;
        }

        target.replaceChildren(...entries.map((entry) => {
            const item = document.createElement("span");
            item.className = "chart-legend-item";
            const marker = document.createElement("span");
            marker.className = "legend-icon";
            marker.style.width = "14px";
            marker.style.height = "14px";
            marker.style.borderRadius = "999px";
            marker.style.background = entry.color;
            const label = document.createElement("span");
            label.textContent = entry.label;
            item.append(marker, label);
            return item;
        }));
    };

    const chartTheme = () => {
        const isDark = document.body.classList.contains("theme-dark");
        return {
            grid: isDark ? "#203447" : "#dce9f7",
            text: isDark ? "#93a7c1" : "#67809f",
            axis: isDark ? "#2b465f" : "#ccdcef",
            label: isDark ? "#e8f1fb" : "#24476f"
        };
    };

    const drawAxisLabels = (svg, labels, padding, width, height, theme) => {
        const innerWidth = width - padding.left - padding.right;
        const innerHeight = height - padding.top - padding.bottom;
        const xAxisLabel = createSvgElement("text", {
            x: padding.left + innerWidth / 2,
            y: height - 10,
            fill: theme.text,
            "font-size": "11",
            "font-weight": "900",
            "letter-spacing": ".08em",
            "text-anchor": "middle"
        });
        xAxisLabel.textContent = labels.x;
        svg.appendChild(xAxisLabel);

        const yCenter = padding.top + innerHeight / 2;
        const yAxisLabel = createSvgElement("text", {
            x: 16,
            y: yCenter,
            fill: theme.text,
            "font-size": "11",
            "font-weight": "900",
            "letter-spacing": ".08em",
            "text-anchor": "middle",
            "dominant-baseline": "middle",
            transform: `rotate(-90 16 ${yCenter})`
        });
        yAxisLabel.textContent = labels.y;
        svg.appendChild(yAxisLabel);
    };

    const sortStatusRows = (rows) => {
        const selectedSort = statusSort?.value || "value_desc";
        return rows.slice().sort((left, right) => {
            if (selectedSort === "value_asc") {
                return left.value - right.value;
            }
            if (selectedSort === "name_asc") {
                return left.label.localeCompare(right.label);
            }
            if (selectedSort === "name_desc") {
                return right.label.localeCompare(left.label);
            }
            return right.value - left.value;
        });
    };

    const aggregateByStatus = () => {
        const componentType = statusComponentFilter?.value || "all";
        const grouped = statusRows
            .filter((row) => componentType === "all" || row.componentType.toUpperCase() === componentType)
            .reduce((map, row) => {
                map.set(row.status, (map.get(row.status) || 0) + row.value);
                return map;
            }, new Map());

        return sortStatusRows(Array.from(grouped)
            .map(([label, value], index) => ({ label, value, color: colorForStatus(label, index) })));
    };

    const drawStatusChart = () => {
        if (!statusChart) {
            return;
        }

        const rows = aggregateByStatus();
        const theme = chartTheme();
        const width = 720;
        const height = 360;
        const padding = { top: 24, right: 24, bottom: 88, left: 68 };
        const innerWidth = width - padding.left - padding.right;
        const innerHeight = height - padding.top - padding.bottom;
        const maxValue = Math.max(1, ...rows.map((row) => row.value));
        const barGap = 14;
        const barWidth = rows.length
            ? Math.max(34, (innerWidth - (rows.length - 1) * barGap) / rows.length)
            : innerWidth;

        statusChart.replaceChildren();
        statusChart.setAttribute("viewBox", `0 0 ${width} ${height}`);
        statusChart.setAttribute("preserveAspectRatio", "xMidYMid meet");

        for (let index = 0; index <= 4; index += 1) {
            const value = Math.round((maxValue / 4) * index);
            const y = padding.top + innerHeight - (value / maxValue) * innerHeight;
            statusChart.appendChild(createSvgElement("line", {
                x1: padding.left,
                y1: y,
                x2: width - padding.right,
                y2: y,
                stroke: theme.grid,
                "stroke-dasharray": index === 0 ? "0" : "4 6"
            }));
            const label = createSvgElement("text", {
                x: padding.left - 10,
                y: y + 4,
                fill: theme.text,
                "font-size": "10",
                "font-weight": "800",
                "text-anchor": "end"
            });
            label.textContent = value.toLocaleString();
            statusChart.appendChild(label);
        }

        rows.forEach((row, index) => {
            const x = padding.left + index * (barWidth + barGap);
            const barHeight = (row.value / maxValue) * innerHeight;
            const y = padding.top + innerHeight - barHeight;
            statusChart.appendChild(createSvgElement("rect", {
                x,
                y,
                width: barWidth,
                height: Math.max(3, barHeight),
                rx: "10",
                fill: row.color
            }));
            const valueLabel = createSvgElement("text", {
                x: x + barWidth / 2,
                y: y - 8,
                fill: theme.label,
                "font-size": "12",
                "font-weight": "900",
                "text-anchor": "middle"
            });
            valueLabel.textContent = row.value.toLocaleString();
            statusChart.appendChild(valueLabel);
            const nameLabel = createSvgElement("text", {
                x: x + barWidth / 2,
                y: height - 42,
                fill: theme.text,
                "font-size": "10",
                "font-weight": "900",
                "text-anchor": "middle"
            });
            nameLabel.textContent = row.label.length > 12 ? `${row.label.slice(0, 12)}...` : row.label;
            statusChart.appendChild(nameLabel);
        });

        drawAxisLabels(statusChart, { x: "STATUS", y: "UNITS" }, padding, width, height, theme);
        renderLegend(statusLegend, rows.map((row) => ({ label: row.label, color: row.color })));
    };

    const drawExpiryChart = () => {
        if (!expiryChart) {
            return;
        }

        const selectedStatus = expiryStatusFilter?.value || "all";
        const filteredRows = expiryRows.filter((row) => selectedStatus === "all" || row.status === selectedStatus);
        const statuses = Array.from(new Set(filteredRows.map((row) => row.status))).sort(compareByStatusOrder);
        const grouped = Array.from(filteredRows.reduce((map, row) => {
            if (!map.has(row.expiryDate)) {
                map.set(row.expiryDate, { expiryDate: row.expiryDate, total: 0, statuses: new Map() });
            }
            const item = map.get(row.expiryDate);
            item.total += row.value;
            item.statuses.set(row.status, (item.statuses.get(row.status) || 0) + row.value);
            return map;
        }, new Map()).values()).sort((left, right) => {
            const selectedSort = expirySort?.value || "date_asc";
            if (selectedSort === "date_desc") {
                return right.expiryDate.localeCompare(left.expiryDate);
            }
            if (selectedSort === "value_desc") {
                return right.total - left.total;
            }
            if (selectedSort === "value_asc") {
                return left.total - right.total;
            }
            return left.expiryDate.localeCompare(right.expiryDate);
        });
        const theme = chartTheme();
        const width = 720;
        const height = 360;
        const padding = { top: 24, right: 24, bottom: 88, left: 68 };
        const innerWidth = width - padding.left - padding.right;
        const innerHeight = height - padding.top - padding.bottom;
        const maxValue = Math.max(1, ...grouped.map((row) => row.total));
        const groupCount = Math.max(1, grouped.length);
        const groupWidth = innerWidth / groupCount;
        const barWidth = grouped.length ? Math.max(4, Math.min(52, groupWidth * 0.62)) : innerWidth;

        expiryChart.replaceChildren();
        expiryChart.setAttribute("viewBox", `0 0 ${width} ${height}`);
        expiryChart.setAttribute("preserveAspectRatio", "xMidYMid meet");

        for (let index = 0; index <= 4; index += 1) {
            const value = Math.round((maxValue / 4) * index);
            const y = padding.top + innerHeight - (value / maxValue) * innerHeight;
            expiryChart.appendChild(createSvgElement("line", {
                x1: padding.left,
                y1: y,
                x2: width - padding.right,
                y2: y,
                stroke: theme.grid,
                "stroke-dasharray": index === 0 ? "0" : "4 6"
            }));
            const label = createSvgElement("text", {
                x: padding.left - 10,
                y: y + 4,
                fill: theme.text,
                "font-size": "10",
                "font-weight": "800",
                "text-anchor": "end"
            });
            label.textContent = value.toLocaleString();
            expiryChart.appendChild(label);
        }

        const labelStep = Math.max(1, Math.ceil(grouped.length / 6));
        grouped.forEach((row, index) => {
            const x = padding.left + index * groupWidth + (groupWidth - barWidth) / 2;
            let yCursor = padding.top + innerHeight;
            statuses.forEach((status, statusIndex) => {
                const value = row.statuses.get(status) || 0;
                if (!value) {
                    return;
                }
                const segmentHeight = (value / maxValue) * innerHeight;
                yCursor -= segmentHeight;
                const segment = createSvgElement("rect", {
                    x,
                    y: yCursor,
                    width: barWidth,
                    height: Math.max(2, segmentHeight),
                    rx: "6",
                    fill: colorForStatus(status, statusIndex)
                });
                const title = createSvgElement("title");
                title.textContent = `${formatDate(row.expiryDate)} - ${status}: ${value}`;
                segment.appendChild(title);
                expiryChart.appendChild(segment);
            });

            if (index % labelStep === 0 || index === grouped.length - 1) {
                const label = createSvgElement("text", {
                    x: x + barWidth / 2,
                    y: height - 42,
                    fill: theme.text,
                    "font-size": "10",
                    "font-weight": "900",
                    "text-anchor": "middle"
                });
                label.textContent = formatDate(row.expiryDate);
                expiryChart.appendChild(label);
            }
        });

        drawAxisLabels(expiryChart, { x: "EXPIRY DATE", y: "UNITS" }, padding, width, height, theme);
        renderLegend(expiryLegend, statuses.map((status, index) => ({
            label: status,
            color: colorForStatus(status, index)
        })));
    };

    const drawCharts = () => {
        drawStatusChart();
        drawExpiryChart();
    };

    statusApply?.addEventListener("click", drawStatusChart);
    expiryApply?.addEventListener("click", drawExpiryChart);
    drawCharts();
    new MutationObserver(drawCharts).observe(document.body, {
        attributes: true,
        attributeFilter: ["class"]
    });
})();
