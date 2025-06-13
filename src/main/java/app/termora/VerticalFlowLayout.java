package app.termora;

import javax.swing.JComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * A vertical flow layout arranges components in a top-to-bottom flow, much
 * like lines of text in a paragraph. Flow layouts are typically used
 * to arrange buttons in a panel. It will arrange
 * buttons top to bottom until no more buttons fit on the same line.
 * Each line is centered.
 */
public class VerticalFlowLayout implements LayoutManager, java.io.Serializable {

    /**
     * This value indicates that each row of components
     * should be left-justified.
     */
    public static final int TOP = 0;

    /**
     * This value indicates that each row of components
     * should be centered.
     */
    public static final int CENTER = 1;

    /**
     * This value indicates that each row of components
     * should be right-justified.
     */
    public static final int BOTTOM = 2;

    /**
     * <code>align</code> is the property that determines
     * how each row distributes empty space.
     * It can be one of the following values:
     * <ul>
     * <code>TOP</code>
     * <code>BOTTOM</code>
     * <code>CENTER</code>
     * <code>LEADING</code>
     * <code>TRAILING</code>
     * </ul>
     *
     * @serial
     * @see #getAlignment
     * @see #setAlignment
     */
    int alisgn;          // This is for 1.1 serialization compatibility

    /**
     * <code>newAlign</code> is the property that determines
     * how each row distributes empty space for the Java 2 platform,
     * v1.2 and greater.
     * It can be one of the following three values:
     * <ul>
     * <code>TOP</code>
     * <code>BOTTOM</code>
     * <code>CENTER</code>
     * <code>LEADING</code>
     * <code>TRAILING</code>
     * </ul>
     *
     * @serial
     * @see #getAlignment
     * @see #setAlignment
     * @since 1.2
     */
    int newAlign;       // This is the one we actually use

    /**
     * The flow layout manager allows a seperation of
     * components with gaps.  The horizontal gap will
     * specify the space between components.
     *
     * @serial
     * @see #getHgap()
     * @see #setHgap(int)
     */
    protected int hgap;

    /**
     * The flow layout manager allows a seperation of
     * components with gaps.  The vertical gap will
     * specify the space between rows.
     *
     * @serial
     * @see #getHgap()
     * @see #setHgap(int)
     */
    protected int vgap;

    /**
     * Constructs a new <code>FlowLayout</code> with a centered alignment and a
     * default 5-unit horizontal and vertical gap.
     */
    public VerticalFlowLayout() {
        this(CENTER, 5, 5);
    }

    /**
     * Constructs a new <code>FlowLayout</code> with the specified
     * alignment and a default 5-unit horizontal and vertical gap.
     * The value of the alignment argument must be one of
     * <code>FlowLayout.TOP</code>, <code>FlowLayout.BOTTOM</code>,
     * or <code>FlowLayout.CENTER</code>.
     *
     * @param align the alignment value
     */
    public VerticalFlowLayout(int align) {
        this(align, 5, 5);
    }

    /**
     * Creates a new flow layout manager with the indicated alignment
     * and the indicated horizontal and vertical gaps.
     * <p/>
     * The value of the alignment argument must be one of
     * <code>FlowLayout.TOP</code>, <code>FlowLayout.BOTTOM</code>,
     * or <code>FlowLayout.CENTER</code>.
     *
     * @param align the alignment value
     * @param hgap  the horizontal gap between components
     * @param vgap  the vertical gap between components
     */
    public VerticalFlowLayout(int align, int hgap, int vgap) {
        this.hgap = hgap;
        this.vgap = vgap;

        setAlignment(align);
    }

    /**
     * Gets the alignment for this layout.
     * Possible values are <code>FlowLayout.TOP</code>,
     * <code>FlowLayout.BOTTOM</code>, <code>FlowLayout.CENTER</code>,
     * <code>FlowLayout.LEADING</code>,
     * or <code>FlowLayout.TRAILING</code>.
     *
     * @return the alignment value for this layout
     * @see java.awt.FlowLayout#setAlignment
     * @since JDK1.1
     */
    public int getAlignment() {
        return newAlign;
    }

    /**
     * Sets the alignment for this layout.
     * Possible values are
     * <ul>
     * <li><code>FlowLayout.TOP</code>
     * <li><code>FlowLayout.BOTTOM</code>
     * <li><code>FlowLayout.CENTER</code>
     * <li><code>FlowLayout.LEADING</code>
     * <li><code>FlowLayout.TRAILING</code>
     * </ul>
     *
     * @param align one of the alignment values shown above
     * @see #getAlignment()
     * @since JDK1.1
     */
    public void setAlignment(int align) {
        this.newAlign = align;
    }

    /**
     * Gets the horizontal gap between components.
     *
     * @return the horizontal gap between components
     * @see java.awt.FlowLayout#setHgap
     * @since JDK1.1
     */
    public int getHgap() {
        return hgap;
    }

    /**
     * Sets the horizontal gap between components.
     *
     * @param hgap the horizontal gap between components
     * @see java.awt.FlowLayout#getHgap
     * @since JDK1.1
     */
    public void setHgap(int hgap) {
        this.hgap = hgap;
    }

    /**
     * Gets the vertical gap between components.
     *
     * @return the vertical gap between components
     * @see java.awt.FlowLayout#setVgap
     * @since JDK1.1
     */
    public int getVgap() {
        return vgap;
    }

    /**
     * Sets the vertical gap between components.
     *
     * @param vgap the vertical gap between components
     * @see java.awt.FlowLayout#getVgap
     * @since JDK1.1
     */
    public void setVgap(int vgap) {
        this.vgap = vgap;
    }

    /**
     * Adds the specified component to the layout. Not used by this class.
     *
     * @param name the name of the component
     * @param comp the component to be added
     */
    public void addLayoutComponent(String name, Component comp) {
    }

    /**
     * Removes the specified component from the layout. Not used by
     * this class.
     *
     * @param comp the component to remove
     * @see java.awt.Container#removeAll
     */
    public void removeLayoutComponent(Component comp) {
    }

    /**
     * Returns the preferred dimensions for this layout given the
     * <i>visible</i> components in the specified target container.
     *
     * @param target the component which needs to be laid out
     * @return the preferred dimensions to lay out the
     * subcomponents of the specified container
     * @see java.awt.Container
     * @see #minimumLayoutSize
     * @see java.awt.Container#getPreferredSize
     */
    public Dimension preferredLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            Dimension dim = new Dimension(0, 0);
            int nmembers = target.getComponentCount();
            Boolean firstVisibleComponent = true;

            for (int i = 0; i < nmembers; i++) {
                Component m = target.getComponent(i);
                if (m.isVisible()) {
                    Dimension d = m.getPreferredSize();

                    firstVisibleComponent = dialWithDim4PreferredLayoutSize(dim, d, firstVisibleComponent);
                }
            }
            Insets insets = target.getInsets();
            dim.width += insets.left + insets.right + hgap * 2;
            dim.height += insets.top + insets.bottom + vgap * 2;
            return dim;
        }
    }

    protected boolean dialWithDim4PreferredLayoutSize(Dimension dim, Dimension d, boolean firstVisibleComponent) {
        dim.width = Math.max(dim.width, d.width);
        if (firstVisibleComponent) {
            firstVisibleComponent = false;
        } else {
            dim.height += vgap;
        }

        dim.height += d.height;

        return firstVisibleComponent;
    }

    /**
     * Returns the minimum dimensions needed to layout the <i>visible</i>
     * components contained in the specified target container.
     *
     * @param target the component which needs to be laid out
     * @return the minimum dimensions to lay out the
     * subcomponents of the specified container
     * @see #preferredLayoutSize
     * @see java.awt.Container
     * @see java.awt.Container#doLayout
     */
    public Dimension minimumLayoutSize(Container target) {
        synchronized (target.getTreeLock()) {
            Dimension dim = new Dimension(0, 0);
            int nmembers = target.getComponentCount();
            boolean firstVisibleComponent = true;

            for (int i = 0; i < nmembers; i++) {
                Component m = target.getComponent(i);
                if (m.isVisible()) {
                    Dimension d = m.getMinimumSize();

                    firstVisibleComponent = dialWithDim4MinimumLayoutSize(dim, d, i, firstVisibleComponent);
                }
            }
            Insets insets = target.getInsets();
            dim.width += insets.left + insets.right + hgap * 2;
            dim.height += insets.top + insets.bottom + vgap * 2;
            return dim;
        }
    }

    protected boolean dialWithDim4MinimumLayoutSize(Dimension dim, Dimension d, int i, boolean firstVisibleComponent) {
        dim.width = Math.max(dim.width, d.width);
        if (i > 0) {
            dim.height += vgap;
        }
        dim.height += d.height;

        return firstVisibleComponent;
    }

    /**
     * Centers the elements in the specified row, if there is any slack.
     *
     * @param target   the component which needs to be moved
     * @param x        the x coordinate
     * @param y        the y coordinate
     * @param width    the width dimensions
     * @param height   the height dimensions
     * @param rowStart the beginning of the row
     * @param rowEnd   the the ending of the row
     */
    private void moveComponents(Container target, int x, int y, int width, int height,
                                int rowStart, int rowEnd, boolean ltr) {
        synchronized (target.getTreeLock()) {
            switch (newAlign) {
                case TOP:
                    y += ltr ? 0 : height;
                    break;
                case CENTER:
                    y += height / 2;
                    break;
                case BOTTOM:
                    y += ltr ? height : 0;
                    break;
            }
            for (int i = rowStart; i < rowEnd; i++) {
                Component m = target.getComponent(i);
                if (m.isVisible()) {
                    if (ltr) {
                        m.setLocation(x + (width - m.getWidth()) / 2, y);
                    } else {
                        m.setLocation(x + (width - m.getWidth()) / 2, target.getHeight() - y - m.getHeight());
                    }
                    y += m.getHeight() + vgap;
                }
            }
        }
    }

    /**
     * Lays out the container. This method lets each component take
     * its preferred size by reshaping the components in the
     * target container in order to satisfy the alignment of
     * this <code>FlowLayout</code> object.
     *
     * @param target the specified component being laid out
     * @see java.awt.Container
     * @see java.awt.Container#doLayout
     */
    public void layoutContainer(Container target) {
        synchronized (target.getTreeLock()) {
            Insets insets = target.getInsets();

            int maxlen = getMaxLen4LayoutContainer(target, insets);
            int nmembers = target.getComponentCount();
            int x = getX4LayoutContainer(insets), y = getY4LayoutContainer(insets);
            int roww = 0, start = 0;

            boolean ltr = target.getComponentOrientation().isLeftToRight();

            int[] rs;
            for (int i = 0; i < nmembers; i++) {
                Component m = target.getComponent(i);
                if (m.isVisible()) {
                    Dimension d = getPreferredSize(target, m);
                    if (target instanceof JComponent t) {
                        m.setSize(t.getWidth(), d.height);
                        d.width = m.getWidth();
                    } else {
                        m.setSize(d.width, d.height);
                    }

                    rs = dealWithDim4LayoutContainer(target, insets, d, x, y, roww, start, maxlen, i, ltr);
                    x = rs[0];
                    y = rs[1];
                    roww = rs[2];
                    start = rs[3];
                }
            }

            dealWithMC4LayoutContainer(target, insets, x, y, roww, start, maxlen, nmembers, ltr);
        }
    }

    protected Dimension getPreferredSize(Container target, Component m) {
        return m.getPreferredSize();
    }

    protected void dealWithMC4LayoutContainer(Container target, Insets insets, int x, int y, int roww, int start, int maxlen, int nmembers, boolean ltr) {
        moveComponents(target, x, insets.top + vgap, roww, maxlen - y, start, nmembers, ltr);
    }

    protected int[] dealWithDim4LayoutContainer(Container target, Insets insets, Dimension d, int x, int y, int roww, int start, int maxlen, int i, boolean ltr) {
        if ((y == 0) || ((y + d.height) <= maxlen)) {
            if (y > 0) y += vgap;
            y += d.height;
            roww = Math.max(roww, d.width);
        } else {
            moveComponents(target, x, insets.top + vgap, roww, maxlen - y, start, i, ltr);
            y = d.height;
            x += hgap + roww;
            roww = d.width;
            start = i;
        }
        return new int[]{x, y, roww, start};
    }

    protected int getMaxLen4LayoutContainer(Container target, Insets insets) {
        return target.getHeight() - (insets.top + insets.bottom + vgap * 2);
    }

    protected int getX4LayoutContainer(Insets insets) {
        return insets.left + hgap;
    }

    protected int getY4LayoutContainer(Insets insets) {
        return 0;
    }


    /**
     * Returns a string representation of this <code>FlowLayout</code>
     * object and its values.
     *
     * @return a string representation of this layout
     */
    public String toString() {
        String str = "";
        switch (this.newAlign) {
            case TOP:
                str = ",align=top";
                break;
            case CENTER:
                str = ",align=center";
                break;
            case BOTTOM:
                str = ",align=bottom";
                break;
        }

        return getClass().getName() + "[hgap=" + hgap + ",vgap=" + vgap + str + "]";
    }
}