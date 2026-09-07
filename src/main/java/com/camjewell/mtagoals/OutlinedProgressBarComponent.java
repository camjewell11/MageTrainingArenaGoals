package com.camjewell.mtagoals;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.TextComponent;

/**
 * A simple filled progress bar with outlined (readable-over-fill) left/center/right labels, and
 * an optional second "pending" segment immediately after the main fill - e.g. to show how much
 * further a not-yet-confirmed amount (gold held but not yet deposited) would push progress.
 */
class OutlinedProgressBarComponent implements LayoutableRenderableEntity
{
	private static final int BAR_HEIGHT = 16;
	private static final int TEXT_PADDING = 4;

	private double percentage = 0.0;
	private double pendingPercentage = 0.0;
	private String leftLabel = "";
	private String centerLabel = "";
	private String rightLabel = "";
	private Color foregroundColor = Color.GREEN;
	private Color pendingColor = null;
	private Color backgroundColor = new Color(61, 56, 49);

	private Point preferredLocation = new Point();
	private Dimension preferredSize = new Dimension(ComponentConstants.STANDARD_WIDTH, BAR_HEIGHT);
	private final Rectangle bounds = new Rectangle();

	void setPercentage(double percentage)
	{
		this.percentage = Math.max(0, Math.min(1, percentage));
	}

	/**
	 * Fraction of the bar (beyond {@link #percentage}) to fill with {@link #pendingColor},
	 * clamped so the two segments together never exceed 100%. 0 (the default) draws no pending
	 * segment at all.
	 */
	void setPendingPercentage(double pendingPercentage)
	{
		this.pendingPercentage = Math.max(0, pendingPercentage);
	}

	void setPendingColor(Color c)
	{
		this.pendingColor = c;
	}

	void setLeftLabel(String s)
	{
		this.leftLabel = s == null ? "" : s;
	}

	void setCenterLabel(String s)
	{
		this.centerLabel = s == null ? "" : s;
	}

	void setRightLabel(String s)
	{
		this.rightLabel = s == null ? "" : s;
	}

	void setForegroundColor(Color c)
	{
		this.foregroundColor = c;
	}

	void setBackgroundColor(Color c)
	{
		this.backgroundColor = c;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		int x = preferredLocation.x;
		int y = preferredLocation.y;
		int width = preferredSize.width;
		int height = preferredSize.height > 0 ? preferredSize.height : BAR_HEIGHT;

		graphics.setColor(backgroundColor);
		graphics.fillRect(x, y, width, height);

		int fillWidth = (int) (width * percentage);
		graphics.setColor(foregroundColor);
		graphics.fillRect(x, y, fillWidth, height);

		if (pendingPercentage > 0 && pendingColor != null)
		{
			int pendingEndWidth = (int) (width * Math.min(percentage + pendingPercentage, 1.0));
			int pendingWidth = pendingEndWidth - fillWidth;
			if (pendingWidth > 0)
			{
				graphics.setColor(pendingColor);
				graphics.fillRect(x + fillWidth, y, pendingWidth, height);
			}
		}

		FontMetrics fm = graphics.getFontMetrics();
		int textY = y + (height + fm.getAscent()) / 2 - 1;

		if (!leftLabel.isEmpty())
		{
			drawOutlined(graphics, leftLabel, x + TEXT_PADDING, textY);
		}
		if (!centerLabel.isEmpty())
		{
			int w = fm.stringWidth(centerLabel);
			drawOutlined(graphics, centerLabel, x + (width - w) / 2, textY);
		}
		if (!rightLabel.isEmpty())
		{
			int w = fm.stringWidth(rightLabel);
			drawOutlined(graphics, rightLabel, x + width - w - TEXT_PADDING, textY);
		}

		bounds.setBounds(x, y, width, height);
		return new Dimension(width, height);
	}

	private void drawOutlined(Graphics2D graphics, String text, int x, int y)
	{
		TextComponent t = new TextComponent();
		t.setText(text);
		t.setColor(Color.WHITE);
		t.setOutline(true);
		t.setPosition(new Point(x, y));
		t.render(graphics);
	}

	@Override
	public Rectangle getBounds()
	{
		return bounds;
	}

	@Override
	public void setPreferredLocation(Point preferredLocation)
	{
		this.preferredLocation = preferredLocation;
	}

	@Override
	public void setPreferredSize(Dimension preferredSize)
	{
		this.preferredSize = preferredSize;
	}
}
