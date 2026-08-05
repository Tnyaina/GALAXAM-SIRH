function toChartPoints(data, width, height, padding, minValue, maxValue) {
  const safeRange = maxValue - minValue || 1;
  const stepX = data.length > 1 ? (width - padding * 2) / (data.length - 1) : 0;

  return data.map((value, index) => {
    const x = padding + index * stepX;
    const normalized = (value - minValue) / safeRange;
    const y = height - padding - normalized * (height - padding * 2);
    return { x, y, value };
  });
}

function linePath(points) {
  return points
    .map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`)
    .join(' ');
}

function areaPath(points, height, padding) {
  if (!points.length) return '';

  const first = points[0];
  const last = points[points.length - 1];

  return [
    `M ${first.x} ${height - padding}`,
    `L ${first.x} ${first.y}`,
    ...points.slice(1).map((point) => `L ${point.x} ${point.y}`),
    `L ${last.x} ${height - padding}`,
    'Z',
  ].join(' ');
}

export function LineTrendChart({ labels, datasets, height = 250 }) {
  const width = 760;
  const padding = 26;
  const allValues = datasets.flatMap((dataset) => dataset.data);
  const min = Math.min(0, ...allValues);
  const max = Math.max(...allValues, 1);

  return (
    <div className="w-full overflow-x-auto">
      <svg
        className="min-w-[640px] w-full"
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label="Revenue trend chart"
      >
        {[0, 1, 2, 3, 4].map((line) => {
          const y = padding + ((height - padding * 2) / 4) * line;
          return (
            <line
              key={line}
              x1={padding}
              x2={width - padding}
              y1={y}
              y2={y}
              stroke="var(--chart-grid)"
              strokeDasharray="4 6"
            />
          );
        })}

        {labels.map((label, index) => {
          const x =
            padding + (labels.length > 1 ? ((width - padding * 2) / (labels.length - 1)) * index : 0);
          return (
            <text
              key={label}
              x={x}
              y={height - 6}
              textAnchor="middle"
              fill="var(--chart-axis)"
              fontSize="12"
              fontWeight="600"
            >
              {label}
            </text>
          );
        })}

        {datasets.map((dataset) => {
          const points = toChartPoints(dataset.data, width, height, padding, min, max);
          return (
            <g key={dataset.name}>
              <path d={areaPath(points, height, padding)} fill={dataset.fill} opacity="0.55" />
              <path d={linePath(points)} fill="none" stroke={dataset.color} strokeWidth="3" strokeLinecap="round" />
              {points.map((point, index) => (
                <circle
                  key={`${dataset.name}-${index}`}
                  cx={point.x}
                  cy={point.y}
                  r="4"
                  fill="var(--chart-point-fill)"
                  stroke={dataset.color}
                  strokeWidth="2"
                />
              ))}
            </g>
          );
        })}
      </svg>
    </div>
  );
}

export function GroupedBarChart({ labels, datasets, height = 240 }) {
  const width = 760;
  const padding = 30;
  const groupWidth = (width - padding * 2) / labels.length;
  const barWidth = Math.min(18, groupWidth / (datasets.length + 1));
  const maxValue = Math.max(...datasets.flatMap((dataset) => dataset.data), 1);

  return (
    <div className="w-full overflow-x-auto">
      <svg
        className="min-w-[640px] w-full"
        viewBox={`0 0 ${width} ${height}`}
        role="img"
        aria-label="Weekly conversion chart"
      >
        {[0, 1, 2, 3, 4].map((line) => {
          const y = padding + ((height - padding * 2) / 4) * line;
          return (
            <line
              key={line}
              x1={padding}
              x2={width - padding}
              y1={y}
              y2={y}
              stroke="var(--chart-grid)"
              strokeDasharray="4 6"
            />
          );
        })}

        {labels.map((label, index) => {
          const baseX = padding + index * groupWidth;
          return (
            <g key={label}>
              <text
                x={baseX + groupWidth / 2}
                y={height - 6}
                textAnchor="middle"
                fill="var(--chart-axis)"
                fontSize="12"
                fontWeight="600"
              >
                {label}
              </text>

              {datasets.map((dataset, datasetIndex) => {
                const value = dataset.data[index];
                const barHeight = (value / maxValue) * (height - padding * 2);
                const x =
                  baseX + groupWidth / 2 - (datasets.length * barWidth + (datasets.length - 1) * 6) / 2 +
                  datasetIndex * (barWidth + 6);
                const y = height - padding - barHeight;

                return (
                  <rect
                    key={`${dataset.name}-${label}`}
                    x={x}
                    y={y}
                    width={barWidth}
                    height={barHeight}
                    rx="6"
                    fill={dataset.color}
                  />
                );
              })}
            </g>
          );
        })}
      </svg>
    </div>
  );
}

export function DonutChart({ segments, size = 224, strokeWidth = 26 }) {
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const total = segments.reduce((sum, segment) => sum + segment.value, 0);
  const arcs = segments.map((segment, index) => {
    const dash = (segment.value / total) * circumference;
    const offset = segments
      .slice(0, index)
      .reduce((sum, previous) => sum + (previous.value / total) * circumference, 0);

    return { segment, dash, offset };
  });

  return (
    <div className="flex items-center justify-center">
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img" aria-label="Traffic source split">
        <g transform={`translate(${size / 2}, ${size / 2}) rotate(-90)`}>
          {arcs.map(({ segment, dash, offset }) => (
              <circle
                key={segment.label}
                r={radius}
                cx="0"
                cy="0"
                fill="transparent"
                stroke={segment.color}
                strokeWidth={strokeWidth}
                strokeDasharray={`${dash} ${circumference - dash}`}
                strokeDashoffset={-offset}
                strokeLinecap="butt"
              />
            ))}
        </g>

        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius - strokeWidth / 2 + 4}
          fill="var(--chart-surface)"
          stroke="var(--chart-surface-border)"
          strokeWidth="1"
        />

        <text
          x="50%"
          y="46%"
          textAnchor="middle"
          fill="var(--chart-text)"
          fontSize="28"
          fontWeight="800"
          style={{ fontFamily: 'Syne, sans-serif' }}
        >
          {total}
        </text>
        <text x="50%" y="58%" textAnchor="middle" fill="var(--chart-axis)" fontSize="12" fontWeight="600">
          Sources
        </text>
      </svg>
    </div>
  );
}

export function ChartLegend({ items, columns = 1 }) {
  return (
    <div className={`grid gap-3 ${columns === 2 ? 'md:grid-cols-2' : ''}`}>
      {items.map((item) => (
        <div key={item.label} className="flex items-center justify-between rounded-xl border border-slate-200 bg-slate-50/70 px-3 py-2.5">
          <div className="flex items-center gap-2.5 min-w-0">
            <span className="w-2.5 h-2.5 rounded-full shrink-0" style={{ backgroundColor: item.color }} />
            <p className="text-sm font-semibold text-slate-700 truncate">{item.label}</p>
          </div>
          <div className="text-right shrink-0">
            <p className="text-sm font-bold text-slate-900">{item.value}</p>
            {item.delta && <p className="text-xs font-semibold text-emerald-600">{item.delta}</p>}
          </div>
        </div>
      ))}
    </div>
  );
}
