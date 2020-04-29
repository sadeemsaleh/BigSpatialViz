package algorithms;

import model.Point;
import model.Query;

import java.util.List;

public interface IAlgorithm {

    /**
     * load data into the algorithm incrementally
     *
     * @param points - Point instances with longitude and latitude
     */
    void load(List<Point> points);

    /**
     * answer a query
     *
     * @param - Query query
     * @return - byte[] binary format result message (including preserved HEADER_SIZE header)
     */
    byte[] answerQuery(Query query);
}
