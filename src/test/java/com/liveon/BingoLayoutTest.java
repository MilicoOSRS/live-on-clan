package com.liveon;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import javax.swing.SwingUtilities;
import javax.swing.JTextArea;
import org.junit.Test;
import static org.junit.Assert.*;

public class BingoLayoutTest
{
	@Test
	public void measuringStaffHelpDoesNotResizeComponents() throws Exception
	{
		SwingUtilities.invokeAndWait(() -> {
			BingoPanel panel = new BingoPanel();
			Container admin = panel.administration();
			admin.setSize(215, 350);
			admin.doLayout();
			for (Component component : admin.getComponents()) {
				if (component instanceof JTextArea) {
					Dimension before = component.getSize();
					for (int i = 0; i < 10; i++) {
						assertTrue(component.getPreferredSize().height < 500);
						assertEquals(before, component.getSize());
					}
				}
			}
			assertTrue(admin.getMinimumSize().height < 500);
		});
	}
}
