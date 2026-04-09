import { Column, Pie, setGlobal } from '@antv/g2plot';
setGlobal({ locale: 'zh-CN' });
/**柱状图-无坐标轴 */
export function get_chart_column(id, height) {
    const data = [
        {
            type: '高',
            sales: 0,
        },
        {
            type: '中',
            sales: 0,
        },
        {
            type: '低',
            sales: 0,
        },
    ];

    const columnPlot = new Column(id, {
        data,
        padding: [30, 30, 30, 30],
        height: height,
        xField: 'type',
        yField: 'sales',
        label: {
            position: 'top',
            style: {
                fontWeight: 'bold'
            },
        },
        xAxis: {
            grid: null,
            line: null,
            tickLine: null,
            label: {
                position: 'top',
                style: {
                    fontWeight: 'bold'
                },
            }
        },
        yAxis: false,

        colorField: 'type', // 部分图表使用 seriesField
        color: ({ type }) => {
            if (type === '高') {
                return '#f46464';
            } else if (type === '中') {
                return '#fdbd50';
            } else {
                return '#377cca';
            }

        },
        interval: {
            spacingRatio: 1, // 设置柱子间的间距比例，范围为 0 到 1
        },
        maxColumnWidth: 30,
    });
    columnPlot.render();
    return columnPlot;
}
/**柱状图-有坐标轴 */
export function get_chart_column2(id, height, padding) {
    let data = [];
    for (let i = 0; i < 24; i++) {
        data.push({ time: i, num: 0 })
    }
    const columnPlot = new Column(id, {
        data,
        padding: padding || 'auto',
        height: height,
        xField: 'time',
        yField: 'num',
        color: "#fdbd50",
        xAxis: {
            label: {
                formatter: (v) => {
                    return v.substring(v.lastIndexOf(' ') + 1) + '时';
                }
            }
        },
        maxColumnWidth: 20
    });
    columnPlot.render();
    return columnPlot;
}
/**饼图 */
export function get_chart_pie(id, height, position, padding) {

    const data = [
        { type: '跑水漏水', value: 0 },
        { type: '温度异常', value: 0 },
        { type: '声音异常', value: 0 },
        { type: '线路漏液', value: 0 },
        { type: '压力异常', value: 0 },
        { type: '循环系统', value: 0 },
        { type: '烟雾预警', value: 0 },
        { type: '火焰预警', value: 0 },
        { type: '越界预警', value: 0 },
        { type: '水质预警', value: 0 },
    ];

    const piePlot = new Pie(id, {
        data,
        height: height,
        width: height,
        padding: padding || 'auto',
        appendPadding: [10, 0, 0, 0],
        angleField: 'value',
        colorField: 'type',
        color: ['#3976f0', '#f49434', '#3e86ce', '#5ac1c0', '#54b775', '#3b77f0', '#f8e3c5', '#f56c6c', '#a3c0bc', '#8b8383'],
        radius: 1,
        innerRadius: 0.54,
        legend: { layout: 'horizontal', position: position, flipPage: false, itemSpacing: 2, itemMarginBottom: 4 },
        label: {
            type: 'inner',
            offset: '-50%',
            autoRotate: false,
            style: { textAlign: 'center' },
            formatter: ({ percent }) => `${(percent * 100).toFixed(0)}%`,
        },
        statistic: {
            title: {
                offsetY: -8,
                style: {
                    fontSize: 14
                }
            },
            content: {
                offsetY: -4,
                style: {
                    fontSize: 14
                }
            },
        },
        // 添加 中心统计文本 交互
        interactions: [
            { type: 'element-selected' },
            { type: 'element-active' },
            {
                type: 'pie-statistic-active',
                cfg: {
                    start: [
                        { trigger: 'element:mouseenter', action: 'pie-statistic:change' },
                        { trigger: 'legend-item:mouseenter', action: 'pie-statistic:change' },
                    ],
                    end: [
                        { trigger: 'element:mouseleave', action: 'pie-statistic:reset' },
                        { trigger: 'legend-item:mouseleave', action: 'pie-statistic:reset' },
                    ],
                },
            },
        ],
    });

    piePlot.render();
    return piePlot;
}



