package ch.picturex.filters;

import ch.picturex.SingleEventBus;
import ch.picturex.model.ThumbnailContainer;
import ch.picturex.events.EventZoom;
import de.muspellheim.eventbus.EventBus;
import java.util.Map;

@SuppressWarnings("unused")

public class Zoom implements IFilter {

    private EventBus bus = SingleEventBus.getInstance();

    public void apply(ThumbnailContainer tc, Map<String, Object> parameters) {

        bus.publish(new EventZoom((String)parameters.get("direction")));

    }

}
