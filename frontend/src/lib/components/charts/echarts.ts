/**
 * ECharts con solo lo que usa Xperp: líneas, barras, rejilla, tooltip y leyenda, con el renderer
 * SVG. Importar 'echarts' completo pesa ~1,1 MB; así el chunk baja a una fracción. Si una gráfica
 * nueva necesita otra pieza (markLine, dataZoom, pie...), se registra aquí.
 */
import { init, use } from 'echarts/core';
import { BarChart, LineChart } from 'echarts/charts';
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components';
import { SVGRenderer } from 'echarts/renderers';

use([LineChart, BarChart, GridComponent, TooltipComponent, LegendComponent, SVGRenderer]);

export { init };
