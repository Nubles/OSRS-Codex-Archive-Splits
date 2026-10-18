package com.codex.archive.overlay;

import com.codex.archive.core.Supply;
import com.codex.archive.ui.Sigil;
import com.codex.archive.ui.Theme;
import com.codex.archive.ui.Ui;
import com.codex.archive.ui.View;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.Map;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/** In the lobby, a plaque naming what the pack is short of before the doors close. Hidden when the pack is ready. */
public class PackPlaque extends Overlay
{
	private static final int PAD = 7;

	private volatile View view;
	private volatile boolean enabled;

	public PackPlaque()
	{
		setPosition(OverlayPosition.TOP_LEFT);
	}

	public void show(View view)
	{
		this.view = view;
	}

	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		View v = view;
		if (!enabled || v == null || !v.inLobby)
		{
			return null;
		}
		Map<Supply, Integer> short_ = v.shortfall();
		if (short_.isEmpty())
		{
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<Supply, Integer> e : short_.entrySet())
		{
			if (sb.length() > 0)
			{
				sb.append(", ");
			}
			Supply s = e.getKey();
			int n = e.getValue();
			sb.append(n).append(' ').append(s == Supply.FOOD ? "food"
				: s.getTitle().toLowerCase().replaceAll("s$", "") + (n == 1 ? " dose" : " doses"));
		}
		String head = "Pack short";
		String body = sb.toString();

		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		g.setFont(Theme.title());
		FontMetrics bold = g.getFontMetrics();
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();
		int icon = 14;
		int width = PAD * 2 + icon + 6 + Math.max(bold.stringWidth(head), small.stringWidth(body));
		int height = PAD * 2 + bold.getHeight() + small.getHeight();
		Plaque.paint(g, width, height, Theme.alpha(Theme.EMBER, 190));
		int x = PAD;
		Sigil.paint(g, x, PAD, icon);
		x += icon + 6;
		int base = PAD + bold.getAscent();
		g.setFont(Theme.title());
		Ui.shadowed(g, head, x, base, Theme.EMBER);
		g.setFont(Theme.small());
		Ui.shadowed(g, body, x, base + small.getHeight() + 2, Theme.TEXT);
		g.dispose();
		return new Dimension(width, height);
	}
}
