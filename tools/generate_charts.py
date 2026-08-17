import matplotlib.pyplot as plt
import matplotlib.font_manager as fm
import numpy as np
import os

font_path = r"D:\文档\字体\Inter-4.1\InterVariable.ttf"
if os.path.exists(font_path):
    fm.fontManager.addfont(font_path)
    prop = fm.FontProperties(fname=font_path)
    plt.rcParams['font.sans-serif'] = [prop.get_name(), 'DejaVu Sans', 'Arial']
    plt.rcParams['font.family'] = 'sans-serif'
else:
    prop = None

# Crisp, Professional Light Theme Settings
plt.rcParams['figure.facecolor'] = '#ffffff'
plt.rcParams['axes.facecolor'] = '#ffffff'
plt.rcParams['axes.edgecolor'] = '#cbd5e1'
plt.rcParams['axes.labelcolor'] = '#475569'
plt.rcParams['xtick.color'] = '#334155'
plt.rcParams['ytick.color'] = '#334155'
plt.rcParams['grid.color'] = '#e2e8f0'
plt.rcParams['grid.alpha'] = 0.8
plt.rcParams['grid.linestyle'] = '--'

models = [
    'Native Baseline',
    'Hev (MTU 1500)',
    'Xray TUN (MTU 1500)',
    'Hev (MTU 8500)',
    'Xray TUN (MTU 8500)'
]
models_reversed = list(reversed(models))
y_pos = np.arange(len(models_reversed))
bar_height = 0.35

COLOR_SINGLE = '#3b82f6'  # Blue (Single) - Top bar
COLOR_MULTI = '#f97316'   # Orange (Multi) - Bottom bar
EDGE_SINGLE = '#2563eb'
EDGE_MULTI = '#ea580c'

def render_horizontal_bar_chart(filename, title, xlabel, single_vals, multi_vals, max_x, min_x=0, is_int=True, unit="", xticks=None, legend_loc='lower right'):
    fig, ax = plt.subplots(figsize=(9.2, 4.3), facecolor='#ffffff')
    
    rects_single = ax.barh(y_pos + bar_height/2, single_vals, bar_height, label='Single', color=COLOR_SINGLE, edgecolor=EDGE_SINGLE, linewidth=1.0, zorder=3)
    rects_multi = ax.barh(y_pos - bar_height/2, multi_vals, bar_height, label='Multi', color=COLOR_MULTI, edgecolor=EDGE_MULTI, linewidth=1.0, zorder=3)
    
    ax.set_title(title, fontsize=13, fontweight='bold', color='#0f172a', fontproperties=prop, pad=12)
    ax.set_xlabel(xlabel, fontsize=10.5, fontproperties=prop)
    ax.set_yticks(y_pos)
    ax.set_yticklabels(models_reversed, fontsize=9.5, fontproperties=prop)
    ax.grid(True, zorder=0, axis='x')
    ax.set_xlim(min_x, max_x)
    if xticks is not None:
        ax.set_xticks(xticks)
    
    ax.legend(handles=[rects_single, rects_multi], labels=['Single', 'Multi'], loc=legend_loc, framealpha=0.95, facecolor='#f8fafc', edgecolor='#cbd5e1', prop=prop)
    
    def autolabel_h(rects):
        for rect in rects:
            width = rect.get_width()
            val_str = f'{int(width)}' if is_int else f'{width:.1f}'
            if unit: val_str += f' {unit}'
            ax.annotate(val_str,
                        xy=(width, rect.get_y() + rect.get_height() / 2),
                        xytext=(6, 0), textcoords="offset points",
                        ha='left', va='center', fontsize=8.5, fontweight='bold', color='#1e293b', fontproperties=prop)
            
    autolabel_h(rects_single)
    autolabel_h(rects_multi)
    
    plt.tight_layout()
    plt.savefig(filename, dpi=200, facecolor='#ffffff', edgecolor='none')
    plt.close()

docs_images = r"d:\Develop\repo\clone\XrayGUI\SimpleXray\docs\images"

def generate_round(prefix, wifi_up_s, wifi_up_m, wifi_down_s, wifi_down_m,
                   usb_up_s, usb_up_m, usb_down_s, usb_down_m,
                   loop_s, loop_m,
                   cpu_up_s, cpu_up_m, cpu_down_s, cpu_down_m,
                   mem_up_s, mem_up_m, mem_down_s, mem_down_m, mem_min, mem_max, mem_ticks):
    # Speed
    render_horizontal_bar_chart(os.path.join(docs_images, f"{prefix}wifi_speed_upload.webp"), "5GHz Wi-Fi: Upload speed - Mbps", "Speed (Mbps)", wifi_up_s, wifi_up_m, 530, legend_loc='lower right')
    render_horizontal_bar_chart(os.path.join(docs_images, f"{prefix}wifi_speed_download.webp"), "5GHz Wi-Fi: Download speed - Mbps", "Speed (Mbps)", wifi_down_s, wifi_down_m, 530, legend_loc='lower right')
    render_horizontal_bar_chart(os.path.join(docs_images, f"{prefix}usb_speed_upload.webp"), "USB 3.2 / 4.0: Upload speed - Mbps", "Speed (Mbps)", usb_up_s, usb_up_m, 1220, legend_loc='lower right')
    render_horizontal_bar_chart(os.path.join(docs_images, f"{prefix}usb_speed_download.webp"), "USB 3.2 / 4.0: Download speed - Mbps", "Speed (Mbps)", usb_down_s, usb_down_m, 560, legend_loc='lower right')
    render_horizontal_bar_chart(os.path.join(docs_images, f"{prefix}loopback_speed.webp"), "On-Device Loopback: Core Processing Speed - Gbps", "Speed (Gbps)", loop_s, loop_m, 28, is_int=False, unit="G", legend_loc='lower right')

    # CPU Usage
    cpu_ticks = np.arange(0.0, 1.1, 0.1)
    render_horizontal_bar_chart(os.path.join(docs_images, f"{prefix}cpu_usage_upload.webp"), "Upload CPU usage - %", "CPU (%)", cpu_up_s, cpu_up_m, 1.0, min_x=0.0, is_int=False, unit="%", xticks=cpu_ticks, legend_loc='lower right')
    render_horizontal_bar_chart(os.path.join(docs_images, f"{prefix}cpu_usage_download.webp"), "Download CPU usage - %", "CPU (%)", cpu_down_s, cpu_down_m, 1.0, min_x=0.0, is_int=False, unit="%", xticks=cpu_ticks, legend_loc='lower right')

    # Memory Usage
    render_horizontal_bar_chart(os.path.join(docs_images, f"{prefix}memory_usage_upload.webp"), "Upload MEM usage - MB", "Memory (MB)", mem_up_s, mem_up_m, mem_max, min_x=mem_min, is_int=False, unit="MB", xticks=mem_ticks, legend_loc='lower left')
    render_horizontal_bar_chart(os.path.join(docs_images, f"{prefix}memory_usage_download.webp"), "Download MEM usage - MB", "Memory (MB)", mem_down_s, mem_down_m, mem_max, min_x=mem_min, is_int=False, unit="MB", xticks=mem_ticks, legend_loc='lower left')

# ==================== Round 1 ====================
generate_round(
    prefix="r1_",
    wifi_up_s=[431, 384, 326, 347, 303],
    wifi_up_m=[303, 335, 425, 426, 324],
    wifi_down_s=[318, 300, 336, 297, 297],
    wifi_down_m=[335, 382, 311, 406, 319],
    usb_up_s=[999, 986, 1001, 997, 984],
    usb_up_m=[896, 899, 895, 898, 900],
    usb_down_s=[390, 394, 391, 396, 383],
    usb_down_m=[428, 431, 436, 423, 433],
    loop_s=[22.21, 22.22, 22.18, 21.40, 20.08],
    loop_m=[17.69, 17.95, 18.08, 17.88, 17.93],
    cpu_up_s=[0.0, 0.0, 0.0, 0.0, 0.0],
    cpu_up_m=[0.0, 0.0, 0.0, 0.0, 0.0],
    cpu_down_s=[0.0, 0.0, 0.0, 0.0, 0.0],
    cpu_down_m=[0.0, 0.0, 0.0, 0.0, 0.1],
    mem_up_s=[118.4, 118.4, 118.5, 118.5, 118.3],
    mem_up_m=[118.3, 118.2, 118.4, 118.4, 118.3],
    mem_down_s=[118.4, 118.4, 118.5, 118.5, 118.3],
    mem_down_m=[118.3, 118.2, 118.4, 118.4, 118.3],
    mem_min=115.0, mem_max=119.5, mem_ticks=np.arange(115.0, 119.5, 0.5)
)

# Also generate default (r1) without prefix for backward-compatibility
generate_round(
    prefix="",
    wifi_up_s=[431, 384, 326, 347, 303],
    wifi_up_m=[303, 335, 425, 426, 324],
    wifi_down_s=[318, 300, 336, 297, 297],
    wifi_down_m=[335, 382, 311, 406, 319],
    usb_up_s=[999, 986, 1001, 997, 984],
    usb_up_m=[896, 899, 895, 898, 900],
    usb_down_s=[390, 394, 391, 396, 383],
    usb_down_m=[428, 431, 436, 423, 433],
    loop_s=[22.21, 22.22, 22.18, 21.40, 20.08],
    loop_m=[17.69, 17.95, 18.08, 17.88, 17.93],
    cpu_up_s=[0.0, 0.0, 0.0, 0.0, 0.0],
    cpu_up_m=[0.0, 0.0, 0.0, 0.0, 0.0],
    cpu_down_s=[0.0, 0.0, 0.0, 0.0, 0.0],
    cpu_down_m=[0.0, 0.0, 0.0, 0.0, 0.1],
    mem_up_s=[118.4, 118.4, 118.5, 118.5, 118.3],
    mem_up_m=[118.3, 118.2, 118.4, 118.4, 118.3],
    mem_down_s=[118.4, 118.4, 118.5, 118.5, 118.3],
    mem_down_m=[118.3, 118.2, 118.4, 118.4, 118.3],
    mem_min=115.0, mem_max=119.5, mem_ticks=np.arange(115.0, 119.5, 0.5)
)

# ==================== Round 2 ====================
generate_round(
    prefix="r2_",
    wifi_up_s=[311, 345, 377, 317, 311],
    wifi_up_m=[345, 332, 336, 343, 293],
    wifi_down_s=[327, 292, 318, 297, 264],
    wifi_down_m=[349, 363, 352, 343, 296],
    usb_up_s=[961, 989, 979, 995, 1051],
    usb_up_m=[877, 895, 889, 890, 931],
    usb_down_s=[394, 384, 383, 379, 380],
    usb_down_m=[420, 420, 422, 421, 426],
    loop_s=[20.43, 20.37, 20.96, 21.09, 21.50],
    loop_m=[17.62, 17.96, 17.92, 17.69, 17.92],
    cpu_up_s=[0.4, 0.4, 0.2, 0.2, 0.1],
    cpu_up_m=[0.2, 0.1, 0.3, 0.3, 0.3],
    cpu_down_s=[0.3, 0.6, 0.1, 0.3, 0.3],
    cpu_down_m=[0.3, 0.5, 0.3, 0.2, 0.3],
    mem_up_s=[77.2, 77.2, 77.2, 77.1, 77.0],
    mem_up_m=[77.2, 77.2, 77.2, 77.2, 77.0],
    mem_down_s=[77.2, 77.2, 77.2, 77.1, 77.0],
    mem_down_m=[77.2, 77.2, 77.2, 77.2, 77.0],
    mem_min=75.0, mem_max=79.5, mem_ticks=np.arange(75.0, 79.5, 0.5)
)

print("Generated Round 1 and Round 2 charts successfully.")
