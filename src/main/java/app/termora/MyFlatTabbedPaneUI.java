package app.termora;

import com.formdev.flatlaf.ui.FlatTabbedPaneUI;
import com.formdev.flatlaf.ui.FlatUIUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;

import static com.formdev.flatlaf.FlatClientProperties.*;
import static com.formdev.flatlaf.util.UIScale.scale;

/**
 * 如果要升级 FlatLaf 需要检查是否兼容
 */
@Deprecated
public class MyFlatTabbedPaneUI extends FlatTabbedPaneUI {
    @Override
    protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
        if (tabPane.getTabCount() <= 0 ||
                contentSeparatorHeight == 0 ||
                !clientPropertyBoolean(tabPane, TABBED_PANE_SHOW_CONTENT_SEPARATOR, showContentSeparator))
            return;

        Insets insets = tabPane.getInsets();
        Insets tabAreaInsets = getTabAreaInsets(tabPlacement);

        int x = insets.left;
        int y = insets.top;
        int w = tabPane.getWidth() - insets.right - insets.left;
        int h = tabPane.getHeight() - insets.top - insets.bottom;

        // remove tabs from bounds
        switch (tabPlacement) {
            case BOTTOM:
                h -= calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight);
                h += tabAreaInsets.top;
                break;

            case LEFT:
                x += calculateTabAreaWidth(tabPlacement, runCount, maxTabWidth);
                x -= tabAreaInsets.right;
                w -= (x - insets.left);
                break;

            case RIGHT:
                w -= calculateTabAreaWidth(tabPlacement, runCount, maxTabWidth);
                w += tabAreaInsets.left;
                break;

            case TOP:
            default:
                y += calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight);
                y -= tabAreaInsets.bottom;
                h -= (y - insets.top);
                break;
        }

        // compute insets for separator or full border
        boolean hasFullBorder = clientPropertyBoolean(tabPane, TABBED_PANE_HAS_FULL_BORDER, this.hasFullBorder);
        int sh = scale(contentSeparatorHeight * 100); // multiply by 100 because rotateInsets() does not use floats
        Insets ci = new Insets(0, 0, 0, 0);
        rotateInsets(hasFullBorder ? new Insets(sh, sh, sh, sh) : new Insets(sh, 0, 0, 0), ci, tabPlacement);

        // create path for content separator or full border
        Path2D path = new Path2D.Float(Path2D.WIND_EVEN_ODD);
        path.append(new Rectangle2D.Float(x, y, w, h), false);
        path.append(new Rectangle2D.Float(x + (ci.left / 100f), y + (ci.top / 100f),
                w - (ci.left / 100f) - (ci.right / 100f), h - (ci.top / 100f) - (ci.bottom / 100f)), false);

        // add gap for selected tab to path
        if (getTabType() == TAB_TYPE_CARD && selectedIndex >= 0) {
            float csh = scale((float) contentSeparatorHeight);

            Rectangle tabRect = getTabBounds(tabPane, selectedIndex);
            boolean componentHasFullBorder = false;
            if (tabPane.getComponentAt(selectedIndex) instanceof JComponent c) {
                componentHasFullBorder = c.getClientProperty(TABBED_PANE_HAS_FULL_BORDER) == Boolean.TRUE;
            }
            Rectangle2D.Float innerTabRect = new Rectangle2D.Float(tabRect.x + csh, tabRect.y + csh,
                    componentHasFullBorder ? 0 : tabRect.width - (csh * 2), tabRect.height - (csh * 2));

            // Ensure that the separator outside the tabViewport is present (doesn't get cutoff by the active tab)
            // If left unsolved the active tab is "visible" in the separator (the gap) even when outside the viewport
            if (tabViewport != null)
                Rectangle2D.intersect(tabViewport.getBounds(), innerTabRect, innerTabRect);

            Rectangle2D.Float gap = null;
            if (isHorizontalTabPlacement(tabPlacement)) {
                if (innerTabRect.width > 0) {
                    float y2 = (tabPlacement == TOP) ? y : y + h - csh;
                    gap = new Rectangle2D.Float(innerTabRect.x, y2, innerTabRect.width, csh);
                }
            } else {
                if (innerTabRect.height > 0) {
                    float x2 = (tabPlacement == LEFT) ? x : x + w - csh;
                    gap = new Rectangle2D.Float(x2, innerTabRect.y, csh, innerTabRect.height);
                }
            }

            if (gap != null) {
                path.append(gap, false);

                // fill gap in case that the tab is colored (e.g. focused or hover)
                Color background = getTabBackground(tabPlacement, selectedIndex, true);
                g.setColor(FlatUIUtils.deriveColor(background, tabPane.getBackground()));
                ((Graphics2D) g).fill(gap);
            }
        }

        // paint content separator or full border
        g.setColor(contentAreaColor);
        ((Graphics2D) g).fill(path);

        // repaint selection in scroll-tab-layout because it may be painted before
        // the content border was painted (from BasicTabbedPaneUI$ScrollableTabPanel)
        if (isScrollTabLayout() && selectedIndex >= 0 && tabViewport != null) {
            Rectangle tabRect = getTabBounds(tabPane, selectedIndex);

            // clip to "scrolling sides" of viewport
            // (left and right if horizontal, top and bottom if vertical)
            Shape oldClip = g.getClip();
            Rectangle vr = tabViewport.getBounds();
            if (isHorizontalTabPlacement(tabPlacement))
                g.clipRect(vr.x, 0, vr.width, tabPane.getHeight());
            else
                g.clipRect(0, vr.y, tabPane.getWidth(), vr.height);

            paintTabSelection(g, tabPlacement, selectedIndex, tabRect.x, tabRect.y, tabRect.width, tabRect.height);
            g.setClip(oldClip);
        }
    }


    private boolean isScrollTabLayout() {
        return tabPane.getTabLayoutPolicy() == JTabbedPane.SCROLL_TAB_LAYOUT;
    }

}
