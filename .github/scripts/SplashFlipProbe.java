import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Transform;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

public class SplashFlipProbe {

    private static final int WIDTH = 400;
    private static final int HEIGHT = 150;

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "asis";
        boolean patched = "patched".equals(mode);

        Display display = new Display();
        Shell shell = new Shell(display, SWT.NO_TRIM);
        shell.setLayout(null);
        shell.setBounds(100, 100, WIDTH, HEIGHT);

        Image striped = createStripedImage(display);
        Image toRender = patched ? applyFix(display, striped) : striped;

        RGB rawTop = toRender.getImageData().palette.getRGB(toRender.getImageData().getPixel(WIDTH / 2, HEIGHT / 4));
        System.out.println("mode=" + mode);
        System.out.println("os.name=" + System.getProperty("os.name") + " os.version=" + System.getProperty("os.version"));
        System.out.println("raw top pixel rgb=" + rawTop);

        Composite panel = new Composite(shell, SWT.NONE);
        panel.setBounds(0, 0, WIDTH, HEIGHT);
        panel.setBackgroundImage(toRender);

        shell.open();
        panel.redraw();

        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (!display.readAndDispatch()) {
                Thread.sleep(50);
            }
        }

        Image screen = new Image(display, WIDTH, HEIGHT);
        GC screenGc = new GC(display);
        Point origin = shell.toDisplay(0, 0);
        screenGc.copyArea(screen, origin.x, origin.y);
        screenGc.dispose();

        ImageData snapshot = screen.getImageData();
        boolean renderedTopIsRed = isRed(snapshot, WIDTH / 2, HEIGHT / 4);

        ImageLoader loader = new ImageLoader();
        loader.data = new ImageData[] { snapshot };
        loader.save("probe-result-" + mode + ".png", SWT.IMAGE_PNG);

        System.out.println("rendered top pixel is red: " + renderedTopIsRed);

        toRender.dispose();
        screen.dispose();
        display.dispose();

        if (patched && !renderedTopIsRed) {
            System.exit(1);
        }
    }

    private static boolean isRed(ImageData data, int x, int y) {
        RGB rgb = data.palette.getRGB(data.getPixel(x, y));
        return rgb.red > 150 && rgb.blue < 100;
    }

    private static Image createStripedImage(Display display) {
        ImageData data = new ImageData(WIDTH, HEIGHT, 24, new PaletteData(0xFF0000, 0x00FF00, 0x0000FF));
        RGB red = new RGB(220, 30, 30);
        RGB blue = new RGB(30, 30, 220);
        int redPixel = data.palette.getPixel(red);
        int bluePixel = data.palette.getPixel(blue);
        for (int y = 0; y < HEIGHT; y++) {
            int pixel = y < HEIGHT / 2 ? redPixel : bluePixel;
            for (int x = 0; x < WIDTH; x++) {
                data.setPixel(x, y, pixel);
            }
        }
        return new Image(display, data);
    }

    private static Image applyFix(Display display, Image source) {
        String osName = System.getProperty("os.name", "");
        if (!osName.toLowerCase().contains("mac")) {
            return source;
        }
        int major;
        try {
            major = Integer.parseInt(System.getProperty("os.version", "0").split("\\.")[0]);
        } catch (RuntimeException e) {
            return source;
        }
        if (major != 14) {
            return source;
        }
        Transform transform = new Transform(display);
        GC gc = new GC(source);
        try {
            transform.setElements(1, 0, 0, -1, 0, 0);
            gc.setTransform(transform);
            gc.drawImage(source, 0, -source.getBounds().height);
        } finally {
            gc.dispose();
            transform.dispose();
        }
        return source;
    }
}