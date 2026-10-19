package com.codex.archive.ui;

import com.codex.archive.core.Comparison;
import com.codex.archive.core.Encounter;
import com.codex.archive.core.Run;
import com.codex.archive.core.Split;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;

/**
 * The five encounters of a run as a thread of gilt nodes, top to bottom. Each fallen boss shows
 * its room time, the run's time when it fell and how that stands against the best run; the boss
 * being fought glows teal with its clock running; rows not reached wait in faint ink, naming the
 * two doors they offer.
 */
public class SplitTable extends JComponent
{
	private static final int ROW = 38;
	private static final int PAD = 10;
	private static final int NODE_X = 16;

	private Run run;
	private Comparison comparison;
	private int elapsed;
	private boolean live;
	private int[] bestRooms = new int[Encounter.ROWS];

	public SplitTable()
	{
		setOpaque(false);
		int h = ROW * Encounter.ROWS + PAD * 2;
		setPreferredSize(new Dimension(10, h));
		setMinimumSize(new Dimension(10, h));
	}

	/**
	 * @param run the run to show, live or finished
	 * @param comparison the marks to measure against, or null
	 * @param bestRooms each row's best room time, or -1
	 * @param elapsed ticks since a live run began
	 */
	public void update(Run run, Comparison comparison, int[] bestRooms, int elapsed, boolean live)
	{
		this.run = run;
		this.comparison = comparison;
		this.bestRooms = bestRooms;
		this.elapsed = elapsed;
		this.live = live;
		repaint();
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		Graphics2D g = (Graphics2D) graphics.create();
		Theme.smooth(g);
		int w = getWidth();
		int h = getHeight();
		RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, Theme.RADIUS, Theme.RADIUS);
		g.setPaint(new GradientPaint(0, 0, Theme.CARD_TOP, 0, h, Theme.CARD));
		g.fill(shape);
		g.setColor(live ? Theme.alpha(Theme.TEAL, 110) : Theme.STROKE_SOFT);
		g.draw(shape);

		int reached = reachedRow();
		// The row being fought is the one after the furthest boss down, even if a row before it went untimed.
		int current = live && run != null && reached < Encounter.ROWS ? reached : -1;

		// The thread joining the nodes, gilt as far as the run has come.
		int top = PAD + ROW / 2;
		int bottom = PAD + ROW * (Encounter.ROWS - 1) + ROW / 2;
		g.setStroke(new BasicStroke(2f));
		g.setColor(Theme.STROKE);
		g.drawLine(NODE_X, top, NODE_X, bottom);
		if (reached > 0)
		{
			int end = PAD + ROW * Math.min(reached, Encounter.ROWS - 1) + ROW / 2;
			g.setPaint(new GradientPaint(0, top, Theme.GOLD_BRIGHT, 0, end, Theme.GOLD_DIM));
			g.drawLine(NODE_X, top, NODE_X, end);
		}
		g.setStroke(new BasicStroke(1f));

		for (int row = 0; row < Encounter.ROWS; row++)
		{
			paintRow(g, row, PAD + row * ROW, w, row == current, row < reached);
		}
		g.dispose();
	}

	/** The rows the run has finished, the next one being the one it is on. */
	private int reachedRow()
	{
		return run == null ? 0 : run.reached();
	}

	private void paintRow(Graphics2D g, int row, int y, int w, boolean current, boolean passed)
	{
		Split split = run == null ? null : run.split(row);
		boolean done = split != null && split.isDone();
		boolean failed = !live && split != null && !done;
		// Behind the furthest boss down but with no time: the client never saw this boss fall.
		boolean untimed = passed && !done;
		int mid = y + ROW / 2;

		if (current)
		{
			RoundRectangle2D glow = new RoundRectangle2D.Float(4, y + 2, w - 8, ROW - 4, 8, 8);
			g.setPaint(new GradientPaint(0, y, Theme.alpha(Theme.TEAL, 46), w, y, Theme.alpha(Theme.TEAL, 6)));
			g.fill(glow);
		}

		// The node: a filled gilt diamond once fallen, a teal ring while fighting, a hollow mark ahead.
		Path2D diamond = new Path2D.Float();
		float r = done ? 5.5f : 4.5f;
		diamond.moveTo(NODE_X, mid - r);
		diamond.lineTo(NODE_X + r, mid);
		diamond.lineTo(NODE_X, mid + r);
		diamond.lineTo(NODE_X - r, mid);
		diamond.closePath();
		if (done)
		{
			g.setPaint(new GradientPaint(0, mid - r, Theme.GOLD_BRIGHT, 0, mid + r, Theme.GOLD_DIM));
			g.fill(diamond);
			g.setColor(Theme.INK_DEEP);
			g.draw(diamond);
		}
		else if (current)
		{
			g.setColor(Theme.alpha(Theme.TEAL, 60));
			g.fill(new Ellipse2D.Float(NODE_X - 8, mid - 8, 16, 16));
			g.setColor(Theme.INK_DEEP);
			g.fill(diamond);
			g.setColor(Theme.TEAL);
			g.draw(diamond);
		}
		else
		{
			g.setColor(Theme.INK_DEEP);
			g.fill(diamond);
			g.setColor(failed ? Theme.BLOOD : Theme.STROKE);
			g.draw(diamond);
		}

		int tx = NODE_X + 14;
		int rightW = 74;
		int nameRoom = w - tx - rightW - 8;
		g.setFont(Theme.title());
		FontMetrics bold = g.getFontMetrics();
		g.setFont(Theme.small());
		FontMetrics small = g.getFontMetrics();

		String name = split != null ? split.getEncounter().getTitle() : Encounter.rowLabel(row);
		if (split != null && bold.stringWidth(name) > nameRoom)
		{
			name = split.getEncounter().getShortName();
		}
		Color nameColor = done ? Theme.TEXT : current ? Theme.mix(Theme.TEAL, Theme.TEXT, 0.55) : failed ? Theme.BLOOD : Theme.TEXT_FAINT;
		g.setFont(split != null ? Theme.title() : Theme.small());
		FontMetrics nameFm = split != null ? bold : small;
		Ui.shadowed(g, Ui.clip(name, nameFm, nameRoom), tx, mid - 2, nameColor);

		g.setFont(Theme.small());
		String sub;
		Color subColor = Theme.TEXT_FAINT;
		if (done)
		{
			sub = "room " + Format.ticks(split.roomTicks(0));
			int bestRoom = bestRooms != null && row < bestRooms.length ? bestRooms[row] : -1;
			if (bestRoom >= 0 && split.roomTicks(0) <= bestRoom)
			{
				subColor = Theme.mix(Theme.GOLD, Theme.TEXT_FAINT, 0.2);
				sub = "best room " + Format.ticks(split.roomTicks(0));
			}
			else if (bestRoom >= 0)
			{
				// What the room alone gave away against its best, apart from how the run stands overall;
				// a long room and its delta close up rather than lose the delta's last figures.
				String delta = Format.delta(split.roomTicks(0) - bestRoom);
				sub = small.stringWidth(sub + "  " + delta) <= nameRoom ? sub + "  " + delta
					: small.stringWidth(sub + " " + delta) <= nameRoom ? sub + " " + delta
					: Format.ticks(split.roomTicks(0)) + " room " + delta;
			}
		}
		else if (current)
		{
			sub = split == null ? "walking in" : "fight " + Format.ticks(split.fightTicks(elapsed));
			subColor = Theme.alpha(Theme.TEAL, 200);
		}
		else if (untimed)
		{
			sub = "not timed";
		}
		else if (failed)
		{
			sub = "the run ended here";
			subColor = Theme.alpha(Theme.BLOOD, 200);
		}
		else
		{
			sub = row == 0 ? "always first" : row == Encounter.ROWS - 1 ? "always last" : "left or right door";
		}
		g.setColor(subColor);
		g.drawString(Ui.clip(sub, small, nameRoom), tx, mid + small.getAscent() + 3);

		// The right column: the run's time at this boss, and the delta under it. The row being
		// fought shows the best's time here as the mark to beat; the crest already runs the clock.
		int rx = w - 10;
		int bestEnd = comparison == null ? -1 : comparison.end(row);
		String time = null;
		Color timeColor = Theme.TEXT;
		if (done)
		{
			time = Format.ticks(split.getEndTick());
		}
		else if (current)
		{
			time = Format.ticks(bestEnd >= 0 ? bestEnd : elapsed);
			timeColor = bestEnd >= 0 ? Theme.alpha(Theme.TEAL, 190) : Theme.mix(Theme.TEAL, Theme.TEXT, 0.4);
		}
		else if (!untimed && !failed && bestEnd >= 0)
		{
			time = Format.ticks(bestEnd);
			timeColor = Theme.TEXT_FAINT;
		}
		if (time != null)
		{
			g.setFont(Theme.body());
			FontMetrics body = g.getFontMetrics();
			Ui.shadowed(g, time, rx - body.stringWidth(time), mid - 2, timeColor);
		}

		Integer delta = done && comparison != null ? comparison.delta(run, row) : null;
		if (current && bestEnd >= 0 && elapsed > bestEnd)
		{
			// While fighting, only show a delta once the run is already behind: being ahead is not known until the boss falls.
			delta = elapsed - bestEnd;
		}
		if (delta != null)
		{
			paintPill(g, Format.delta(delta), Theme.delta(delta), rx, mid + 3);
		}
		else if (!done && time != null && bestEnd >= 0)
		{
			g.setFont(Theme.small());
			String label = current ? "to beat" : comparison.getMode() == Comparison.Mode.PERSONAL_BEST ? "best"
				: comparison.getMode().getShortLabel().toLowerCase();
			g.setColor(current ? Theme.alpha(Theme.TEAL, 170) : Theme.TEXT_FAINT);
			g.drawString(label, rx - small.stringWidth(label), mid + small.getAscent() + 3);
		}
	}

	/** A small rounded tag, right-aligned at x, for a delta. */
	private static void paintPill(Graphics2D g, String text, Color color, int right, int top)
	{
		g.setFont(Theme.small());
		FontMetrics fm = g.getFontMetrics();
		int pw = fm.stringWidth(text) + 10;
		int ph = fm.getHeight() + 1;
		RoundRectangle2D pill = new RoundRectangle2D.Float(right - pw, top, pw, ph, ph, ph);
		g.setColor(Theme.alpha(color, 40));
		g.fill(pill);
		g.setColor(Theme.alpha(color, 130));
		g.draw(pill);
		g.setColor(color);
		g.drawString(text, right - pw + 5, top + fm.getAscent());
	}
}
