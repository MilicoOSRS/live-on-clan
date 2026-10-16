package com.liveon;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import net.runelite.client.ui.FontManager;

/** One palette for every tab: green is "yours / active", orange is headings and alerts. */
final class LiveOnTheme
{
	static final Color BACKGROUND = new Color(30, 30, 30);
	static final Color CARD = new Color(40, 40, 40);
	static final Color CARD_HOVER = new Color(48, 48, 48);
	static final Color CARD_EDGE = new Color(70, 70, 70);
	static final Color DIVIDER = new Color(52, 52, 52);

	static final Color TEXT = new Color(232, 232, 232);
	static final Color TEXT_MUTED = new Color(170, 170, 170);
	static final Color TEXT_FAINT = new Color(118, 118, 118);

	static final Color GREEN = new Color(166, 226, 46);
	static final Color GREEN_SOFT = new Color(38, 48, 22);
	static final Color ORANGE = new Color(255, 152, 31);
	static final Color RED = new Color(232, 116, 106);
	/** Solid background for the one primary action button of a section. */
	static final Color ACTION = new Color(190, 104, 0);

	static final Color GOLD = new Color(226, 176, 74);
	static final Color GOLD_SOFT = new Color(46, 40, 26);
	static final Color SILVER = new Color(185, 188, 194);
	static final Color BRONZE = new Color(192, 122, 69);

	private static final int ARC = 10;
	private static final int ACCENT_WIDTH = 3;
	private static final String ACCENT_KEY = "liveon.accent";

	private LiveOnTheme()
	{
	}

	// RuneLite's fonts are pixel fonts: they are only crisp at their own size, so
	// never deriveFont() them to a fractional size - pick one of these instead.
	static Font small()
	{
		return FontManager.getRunescapeSmallFont();
	}

	static Font regular()
	{
		return FontManager.getRunescapeFont();
	}

	static Font bold()
	{
		return FontManager.getRunescapeBoldFont();
	}

	/** Remembers the edge color a card should show once {@link #roundCard} wraps it. */
	private static void accent(JComponent component, Color accent)
	{
		component.putClientProperty(ACCENT_KEY, accent);
	}

	/**
	 * Turns the outermost panel of a card into a rounded card: the border paints the rounded
	 * background and the accent edge before any child, and the child panels stop painting their
	 * own square backgrounds so the corners stay visible.
	 */
	static void roundCard(JComponent card, Insets padding)
	{
		Color accent = findAccent(card);
		card.setOpaque(false);
		card.setBorder(new RoundCardBorder(accent, padding));
		clearChildBackgrounds(card);
	}

	static void roundCard(JComponent card, Color accent, Insets padding)
	{
		accent(card, accent);
		roundCard(card, padding);
	}

	private static Color findAccent(Component component)
	{
		if (component instanceof JComponent)
		{
			Object value = ((JComponent) component).getClientProperty(ACCENT_KEY);
			if (value instanceof Color) return (Color) value;
		}
		if (component instanceof Container)
		{
			for (Component child : ((Container) component).getComponents())
			{
				Color found = findAccent(child);
				if (found != null) return found;
			}
		}
		return null;
	}

	private static void clearChildBackgrounds(Container container)
	{
		for (Component child : container.getComponents())
		{
			if (child instanceof JPanel)
			{
				((JPanel) child).setOpaque(false);
				clearChildBackgrounds((JPanel) child);
			}
		}
	}

	private static final class RoundCardBorder extends AbstractBorder
	{
		private final Color accent;
		private final Insets padding;

		private RoundCardBorder(Color accent, Insets padding)
		{
			this.accent = accent;
			this.padding = padding;
		}

		@Override
		public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			RoundRectangle2D shape = new RoundRectangle2D.Float(x, y, width, height, ARC, ARC);
			g.setColor(component.getBackground());
			g.fill(shape);
			if (accent != null)
			{
				g.clip(shape);
				g.setColor(accent);
				g.fill(new Rectangle(x, y, ACCENT_WIDTH, height));
			}
			g.dispose();
		}

		@Override
		public Insets getBorderInsets(Component component)
		{
			int left = padding.left + (accent == null ? 0 : ACCENT_WIDTH);
			return new Insets(padding.top, left, padding.bottom, padding.right);
		}

		@Override
		public Insets getBorderInsets(Component component, Insets insets)
		{
			Insets resolved = getBorderInsets(component);
			insets.set(resolved.top, resolved.left, resolved.bottom, resolved.right);
			return insets;
		}

		@Override
		public boolean isBorderOpaque()
		{
			return false;
		}
	}

	/** Rounded pill used for the main navigation; the selected one gets the green underline. */
	static final class PillButton extends JButton
	{
		private boolean active;

		PillButton(String text, javax.swing.Icon icon)
		{
			super(text, icon);
			setContentAreaFilled(false);
			setBorderPainted(false);
			setFocusPainted(false);
			setOpaque(false);
			setFont(small());
			setBorder(BorderFactory.createEmptyBorder(5, 1, 6, 1));
		}

		void setActive(boolean active)
		{
			this.active = active;
			setForeground(active ? GREEN : TEXT_MUTED);
			repaint();
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			boolean hover = getModel().isRollover();
			g.setColor(active ? CARD_HOVER : hover ? CARD : BACKGROUND);
			g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), ARC, ARC));
			if (active)
			{
				g.setColor(GREEN);
				g.fillRoundRect(8, getHeight() - 3, getWidth() - 16, 2, 2, 2);
			}
			g.dispose();
			super.paintComponent(graphics);
		}
	}

	/**
	 * Flat sub-tabs (MVP Drops/EHB/EHP, staff sections): a thin accent underline below the
	 * selected tab, instead of the default raised Swing tabs or a filled highlight box.
	 */
	static void styleTabs(JTabbedPane pane, Color accent)
	{
		pane.setUI(new FlatTabsUI(accent));
		pane.setOpaque(false);
		pane.setFont(small());
	}

	/** Tab buttons placed inside a styled tab pane paint nothing themselves. */
	static void styleTabButton(JButton button, boolean selected, Color accent)
	{
		button.setContentAreaFilled(false);
		button.setBorderPainted(false);
		button.setFocusPainted(false);
		button.setOpaque(false);
		button.setFont(small());
		button.setForeground(selected ? accent : TEXT_MUTED);
		button.setBorder(BorderFactory.createEmptyBorder(4, 7, 6, 7));
		// Rollover only flips a model flag; repaint so the hover underline in
		// FlatTabsUI.paintTabBackground actually shows up while the mouse is over the tab.
		for (java.awt.event.MouseListener listener : button.getMouseListeners())
		{
			if (listener instanceof RolloverRepainter) button.removeMouseListener(listener);
		}
		button.addMouseListener(new RolloverRepainter());
	}

	private static final class RolloverRepainter extends java.awt.event.MouseAdapter
	{
		@Override
		public void mouseEntered(java.awt.event.MouseEvent event) { repaintParent(event); }

		@Override
		public void mouseExited(java.awt.event.MouseEvent event) { repaintParent(event); }

		private static void repaintParent(java.awt.event.MouseEvent event)
		{
			Container parent = ((JComponent) event.getSource()).getParent();
			if (parent != null) parent.repaint();
		}
	}

	private static final class FlatTabsUI extends BasicTabbedPaneUI
	{
		private static final int UNDERLINE_HEIGHT = 2;
		private static final int UNDERLINE_MARGIN = 2;

		private final Color accent;

		private FlatTabsUI(Color accent)
		{
			this.accent = accent;
		}

		@Override
		protected void installDefaults()
		{
			super.installDefaults();
			tabInsets = new Insets(4, 7, 6, 7);
			selectedTabPadInsets = new Insets(0, 0, 0, 0);
			tabAreaInsets = new Insets(0, 0, 6, 0);
			contentBorderInsets = new Insets(4, 0, 0, 0);
		}

		@Override
		protected LayoutManager createLayoutManager()
		{
			// With WRAP_TAB_LAYOUT, stock BasicTabbedPaneUI brings the selected tab's row to the
			// front on every selection change, which reshuffles rows and makes the whole strip
			// jump. Rows should stay put in the order they were added.
			return tabPane.getTabLayoutPolicy() == JTabbedPane.WRAP_TAB_LAYOUT
				? new TabbedPaneLayout()
				{
					@Override
					protected void rotateTabRuns(int tabPlacement, int selectedRun)
					{
					}
				}
				: super.createLayoutManager();
		}

		@Override
		protected void paintTabBackground(Graphics graphics, int placement, int index, int x, int y,
			int width, int height, boolean selected)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			if (selected)
			{
				g.setColor(accent);
				g.fillRoundRect(x + UNDERLINE_MARGIN, y + height - UNDERLINE_HEIGHT,
					Math.max(0, width - UNDERLINE_MARGIN * 2), UNDERLINE_HEIGHT, UNDERLINE_HEIGHT, UNDERLINE_HEIGHT);
			}
			else if (isRollover(index))
			{
				g.setColor(DIVIDER);
				g.fillRoundRect(x + UNDERLINE_MARGIN, y + height - UNDERLINE_HEIGHT,
					Math.max(0, width - UNDERLINE_MARGIN * 2), UNDERLINE_HEIGHT, UNDERLINE_HEIGHT, UNDERLINE_HEIGHT);
			}
			g.dispose();
		}

		private boolean isRollover(int index)
		{
			Component tabComponent = tabPane.getTabComponentAt(index);
			return tabComponent instanceof JButton && ((JButton) tabComponent).getModel().isRollover();
		}

		@Override
		protected void paintTabBorder(Graphics graphics, int placement, int index, int x, int y,
			int width, int height, boolean selected)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(DIVIDER);
			g.drawRoundRect(x, y, width - 1, height - 1 - UNDERLINE_HEIGHT, 4, 4);
			g.dispose();
		}

		@Override
		protected void paintFocusIndicator(Graphics graphics, int placement, Rectangle[] rects, int index,
			Rectangle iconRect, Rectangle textRect, boolean selected)
		{
		}

		@Override
		protected void paintContentBorder(Graphics graphics, int placement, int selectedIndex)
		{
			Rectangle area = getTabAreaBounds();
			graphics.setColor(DIVIDER);
			graphics.fillRect(0, area.y + area.height, tabPane.getWidth(), 1);
		}

		private Rectangle getTabAreaBounds()
		{
			int bottom = 0;
			for (int index = 0; index < tabPane.getTabCount(); index++)
			{
				Rectangle bounds = getTabBounds(tabPane, index);
				bottom = Math.max(bottom, bounds.y + bounds.height);
			}
			return new Rectangle(0, 0, tabPane.getWidth(), bottom + tabAreaInsets.bottom - 2);
		}
	}

	static JLabel sectionTitle(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(ORANGE);
		label.setFont(bold());
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(2, 2, 5, 0));
		return label;
	}

	static String hex(Color color)
	{
		return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
	}

	/** The trophy glyph used for the PBs nav tab, shared with anywhere else it should read "record". */
	static javax.swing.ImageIcon trophyIcon(Color color, int size)
	{
		int supersample = 4;
		int scaled = size * supersample;
		java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(scaled, scaled, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.scale(supersample * (size / 16.0), supersample * (size / 16.0));
		g.setColor(color);
		g.setStroke(new java.awt.BasicStroke(1.4f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
		g.drawRect(5, 2, 6, 7);
		g.drawArc(1, 3, 6, 6, 80, 200);
		g.drawArc(9, 3, 6, 6, -100, 200);
		g.drawLine(8, 9, 8, 13);
		g.drawLine(5, 14, 11, 14);
		g.dispose();
		java.awt.Image downscaled = image.getScaledInstance(size, size, java.awt.Image.SCALE_AREA_AVERAGING);
		return new javax.swing.ImageIcon(downscaled);
	}
}
