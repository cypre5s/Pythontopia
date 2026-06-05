// 镜头：在「世界绘制像素」坐标系（已含 zoom 放大）里跟随目标并按地图边界夹紧。
export class Camera {
  x = 0;
  y = 0;

  constructor(
    private viewWidth: number,
    private viewHeight: number,
    private worldWidth: number,
    private worldHeight: number,
  ) {}

  resize(viewWidth: number, viewHeight: number): void {
    this.viewWidth = viewWidth;
    this.viewHeight = viewHeight;
  }

  /** 让镜头中心对准 (centerX, centerY)，再夹到世界边界内；世界比视口小则居中。 */
  follow(centerX: number, centerY: number): void {
    this.x = clampAxis(centerX - this.viewWidth / 2, this.worldWidth, this.viewWidth);
    this.y = clampAxis(centerY - this.viewHeight / 2, this.worldHeight, this.viewHeight);
  }
}

function clampAxis(value: number, world: number, view: number): number {
  if (world <= view) {
    // 世界比视口小：把世界居中（镜头左上角取负偏移）。
    return -(view - world) / 2;
  }
  if (value < 0) {
    return 0;
  }
  const max = world - view;
  if (value > max) {
    return max;
  }
  return value;
}
