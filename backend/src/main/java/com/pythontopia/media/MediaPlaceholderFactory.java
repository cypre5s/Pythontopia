package com.pythontopia.media;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * 无 apikey 时的「文生图」本地占位生成器：用 AWT 在内存里画一张星露谷像素风的占位卡（确定式，
 * 同一 prompt 永远得到同一张图，便于缓存与演示）。文字用 ASCII 标签避免无 CJK 字体时的豆腐块，
 * 真正的中文说明由前端卡片在图外渲染。
 *
 * <p>这是降级路径产物，{@code degraded=true}；配置讯飞文生图密钥后由 {@code XfyunImageService}
 * 走真实生成，不再走这里。
 */
@Component
public class MediaPlaceholderFactory {

  private static final int SIZE = 512;
  private static final int CELL = 32;

  // 星露谷像素调色板（与前端 --pyt-* token 同源的暖色系），按 prompt 哈希取主色。
  private static final Color[] PALETTE = {
    new Color(0xE9, 0x8A, 0x3C),
    new Color(0x6E, 0xA8, 0x4F),
    new Color(0x4F, 0x8A, 0xA8),
    new Color(0xC9, 0x5A, 0x4A),
    new Color(0x8A, 0x6E, 0xA8),
    new Color(0xD4, 0xAF, 0x37),
  };

  /** 生成确定式占位 PNG 字节。{@code label} 是叠加在图上的 ASCII 短标签（如 KC code）。 */
  public byte[] renderPng(String prompt, String label) {
    int seed = (prompt == null ? "" : prompt).hashCode();
    Color primary = PALETTE[Math.floorMod(seed, PALETTE.length)];
    Color secondary = PALETTE[Math.floorMod(seed / 7 + 3, PALETTE.length)];

    BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

      // 牛皮纸底
      g.setColor(new Color(0xFF, 0xF7, 0xE6));
      g.fillRect(0, 0, SIZE, SIZE);

      // 确定式像素方块图案（按 prompt 派生的伪随机点亮格子）
      long state = seed == 0 ? 0x9E3779B97F4A7C15L : (seed & 0xFFFFFFFFL);
      for (int y = 0; y < SIZE; y += CELL) {
        for (int x = 0; x < SIZE; x += CELL) {
          state = state * 6364136223846793005L + 1442695040888963407L;
          int bucket = (int) ((state >>> 40) % 5);
          if (bucket == 0) {
            g.setColor(primary);
            g.fillRect(x, y, CELL, CELL);
          } else if (bucket == 1) {
            g.setColor(secondary);
            g.fillRect(x + 4, y + 4, CELL - 8, CELL - 8);
          }
        }
      }

      // 金边木框
      g.setColor(new Color(0x6E, 0x4F, 0x1D));
      g.fillRect(0, 0, SIZE, 12);
      g.fillRect(0, SIZE - 12, SIZE, 12);
      g.fillRect(0, 0, 12, SIZE);
      g.fillRect(SIZE - 12, 0, 12, SIZE);

      // 居中标签条
      g.setColor(new Color(0x2A, 0x1A, 0x05, 0xCC));
      g.fillRect(40, SIZE / 2 - 44, SIZE - 80, 88);
      g.setColor(new Color(0xFF, 0xD1, 0x66));
      g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 22));
      g.drawString("AI ILLUSTRATION", 64, SIZE / 2 - 8);
      g.setColor(new Color(0xF1, 0xE5, 0xC8));
      g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
      g.drawString("(local placeholder)", 64, SIZE / 2 + 16);
      if (label != null && !label.isBlank()) {
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        g.drawString(asciiClamp(label), 64, SIZE / 2 + 38);
      }
    } finally {
      g.dispose();
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      ImageIO.write(img, "png", out);
    } catch (IOException ex) {
      throw new UncheckedIOException("failed to encode placeholder png", ex);
    }
    return out.toByteArray();
  }

  /** 去掉非 ASCII 字符，避免无 CJK 字体时画出豆腐块。 */
  private static String asciiClamp(String value) {
    StringBuilder sb = new StringBuilder();
    for (byte b : value.getBytes(StandardCharsets.US_ASCII)) {
      char c = (char) (b & 0x7F);
      sb.append(c >= 32 && c < 127 ? c : ' ');
      if (sb.length() >= 48) {
        break;
      }
    }
    return sb.toString().trim();
  }
}
