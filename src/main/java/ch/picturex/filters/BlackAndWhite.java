package ch.picturex.filters;


import ch.picturex.IFilter;
import ch.picturex.ThumbnailContainer;
import ij.ImagePlus;
import ij.process.ImageConverter;
import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.Map;

public class BlackAndWhite implements IFilter {

    @Override
    public void apply(ThumbnailContainer tc, Map<String, Object> parameters) {
        ImagePlus imp = null;
        try {
            imp = new ImagePlus(tc.getImageWrapper().getFile().getName(),  ImageIO.read(tc.getImageWrapper().getFile()));
            ImageConverter ic = new ImageConverter(imp);
            ic.convertToGray8();
            imp.updateAndDraw();
            tc.getImageWrapper().set(imp.getBufferedImage());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}