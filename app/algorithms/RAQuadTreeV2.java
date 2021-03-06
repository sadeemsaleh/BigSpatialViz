package algorithms;

import model.Point;
import model.Query;
import util.*;
import util.render.*;

import java.util.*;

import static util.Mercator.*;

public class RAQuadTreeV2 implements IAlgorithm {

    public static double highestLevelNodeDimension;

    public static IRenderer renderer;

    public static IErrorMetric errorMetric;

    public class QuadTree {
        public Point sample;

        // children
        public QuadTree northWest;
        public QuadTree northEast;
        public QuadTree southWest;
        public QuadTree southEast;

        public boolean containsPoint(double cX, double cY, double halfDimension, Point point) {
            if (point.getX() >= (cX - halfDimension)
                    && point.getY() >= (cY - halfDimension)
                    && point.getX() < (cX + halfDimension)
                    && point.getY() < (cY + halfDimension)) {
                return true;
            }
            else {
                return false;
            }
        }

        public boolean intersectsBBox(double c1X, double c1Y, double halfDimension1,
                                      double c2X, double c2Y, double halfWidth2, double halfHeight2) {
            // bbox 1
            double left = c1X - halfDimension1;
            double right = c1X + halfDimension1;
            double bottom = c1Y + halfDimension1;
            double top = c1Y - halfDimension1;
            // bbox 2
            double minX = c2X - halfWidth2;
            double maxX = c2X + halfWidth2;
            double minY = c2Y - halfHeight2;
            double maxY = c2Y + halfHeight2;

            // right to the right
            if (minX > right) return false;
            // left to the left
            if (maxX < left) return false;
            // above the bottom
            if (minY > bottom) return false;
            // below the top
            if (maxY < top) return false;

            return true;
        }

        public boolean insert(double cX, double cY, double halfDimension, Point point, int level) {
            // Ignore objects that do not belong in this quad tree
            if (!containsPoint(cX, cY, halfDimension, point)) {
                return false;
            }
            // If this node is leaf and empty, put this point on this node
            if (this.sample == null && this.northWest == null) {
                this.sample = point;
                return true;
            }

            // if boundary is smaller than highestLevelNodeDimension,
            // stop splitting, and make current node a leaf node.
            if (halfDimension * 2 < highestLevelNodeDimension) {
                // at this moment, this node must already have a sample
                return false; // skip this point
            }

            // Otherwise, subdivide
            if (this.northWest == null) {
                this.subdivide();
                // descend current node's point into corresponding quadrant
                this.insertNorthWest(cX, cY, halfDimension, this.sample, level + 1);
                this.insertNorthEast(cX, cY, halfDimension, this.sample, level + 1);
                this.insertSouthWest(cX, cY, halfDimension, this.sample, level + 1);
                this.insertSouthEast(cX, cY, halfDimension, this.sample, level + 1);
                this.sample = null;
            }

            // insert new point into corresponding quadrant
            if (insertNorthWest(cX, cY, halfDimension, point, level + 1)) return true;
            if (insertNorthEast(cX, cY, halfDimension, point, level + 1)) return true;
            if (insertSouthWest(cX, cY, halfDimension, point, level + 1)) return true;
            if (insertSouthEast(cX, cY, halfDimension, point, level + 1)) return true;

            return false;
        }

        boolean insertNorthWest(double _cX, double _cY, double _halfDimension, Point point, int level) {
            double halfDimension = _halfDimension / 2;
            double cX = _cX - halfDimension;
            double cY = _cY - halfDimension;
            return this.northWest.insert(cX, cY, halfDimension, point, level);
        }

        boolean insertNorthEast(double _cX, double _cY, double _halfDimension, Point point, int level) {
            double halfDimension = _halfDimension / 2;
            double cX = _cX + halfDimension;
            double cY = _cY - halfDimension;
            return this.northEast.insert(cX, cY, halfDimension, point, level);
        }

        boolean insertSouthWest(double _cX, double _cY, double _halfDimension, Point point, int level) {
            double halfDimension = _halfDimension / 2;
            double cX = _cX - halfDimension;
            double cY = _cY + halfDimension;
            return this.southWest.insert(cX, cY, halfDimension, point, level);
        }

        boolean insertSouthEast(double _cX, double _cY, double _halfDimension, Point point, int level) {
            double halfDimension = _halfDimension / 2;
            double cX = _cX + halfDimension;
            double cY = _cY + halfDimension;
            return this.southEast.insert(cX, cY, halfDimension, point, level);
        }

        void subdivide() {
            this.northWest = new QuadTree();
            this.northEast = new QuadTree();
            this.southWest = new QuadTree();
            this.southEast = new QuadTree();
            nodesCount += 4;
        }

        class QEntry {
            int level;
            double ncX;
            double ncY;
            double nhalfDimension;
            QuadTree node;
            double benefit; // the benefit value if the take the best move

            QEntry(int _level, double _ncX, double _ncY, double _nhalfDimension, QuadTree _node, double _benefit) {
                level = _level;
                ncX = _ncX;
                ncY = _ncY;
                nhalfDimension = _nhalfDimension;
                node = _node;
                benefit = _benefit;
            }
        }

        /**
         * breadth first search
         *
         * explore nodes with higher estimated benefit first
         * - benefit = gain of quality / cost of sample size
         *
         * @param _ncX
         * @param _ncY
         * @param _nhalfDimension
         * @param _rcX
         * @param _rcY
         * @param _rhalfWidth
         * @param _rhalfHeight
         * @param _rPixelScale
         * @param _targetSampleSize
         * @return
         */
        public List<Point> bfs(double _ncX, double _ncY, double _nhalfDimension,
                               double _rcX, double _rcY, double _rhalfWidth, double _rhalfHeight,
                               double _rPixelScale, int _targetSampleSize) {

            List<Point> result = new ArrayList<>();

            // explore larger estimatedProfit node first
            PriorityQueue<QEntry> queue = new PriorityQueue<>(new Comparator<QEntry>() {
                @Override
                public int compare(QEntry o1, QEntry o2) {
                    if (o2.benefit > o1.benefit)
                        return 1;
                    else if (o2.benefit < o1.benefit)
                        return -1;
                    else
                        return 0;
                }
            });

            double rootBenefit = computeBenefit(this, _ncX, _ncY, _nhalfDimension, _rcX, _rcY, _rhalfWidth, _rhalfHeight, _rPixelScale);
            QEntry rootEntry = new QEntry(0, _ncX, _ncY, _nhalfDimension, this, rootBenefit);
            // add root node
            queue.add(rootEntry);
            int availableSampleSize = _targetSampleSize - Constants.NODE_SAMPLE_SIZE;

            while (queue.size() > 0) {

                // pick the largest benefit node
                QEntry entry = queue.poll();
                int level = entry.level;
                double ncX = entry.ncX;
                double ncY = entry.ncY;
                double nhalfDimension = entry.nhalfDimension;
                QuadTree node = entry.node;
                double benefit = entry.benefit;
                int sampleSize = node.sample == null? 0: Constants.NODE_SAMPLE_SIZE;

                // if the largest estimated benefit is 0 or enough samples, entering collecting samples mode
                if (benefit <= 0.0 || availableSampleSize <= 0) {
                    //-DEBUG-//
//                    System.out.println("[queue] level = " + level);
//                    System.out.println("[queue] benefit = " + benefit);
//                    System.out.println("[queue] sample size = " + sampleSize);
                    //-DEBUG-//
                    if (node.sample != null) {
                        numberOfNodesStoppedAtLevels[level] ++;
                        result.add(node.sample);
                    }
                    continue;
                }

                // otherwise, expand this node
                double cX, cY;
                double halfDimension = nhalfDimension / 2;
                availableSampleSize += sampleSize;

                // northwest
                cX = ncX - halfDimension;
                cY = ncY - halfDimension;
                // ignore this node if the range does not intersect with it
                if (intersectsBBox(cX, cY, halfDimension, _rcX, _rcY, _rhalfWidth, _rhalfHeight)) {
                    double benefitNW = computeBenefit(node.northWest, cX, cY, halfDimension, _rcX, _rcY, _rhalfWidth, _rhalfHeight, _rPixelScale);
                    QEntry entryNW = new QEntry(level + 1, cX, cY, halfDimension, node.northWest, benefitNW);
                    queue.add(entryNW);
                    if (node.northWest.sample != null) {
                        availableSampleSize -= Constants.NODE_SAMPLE_SIZE;
                    }
                }

                // northeast
                cX = ncX + halfDimension;
                cY = ncY - halfDimension;
                // ignore this node if the range does not intersect with it
                if (intersectsBBox(cX, cY, halfDimension, _rcX, _rcY, _rhalfWidth, _rhalfHeight)) {
                    double benefitNE = computeBenefit(node.northEast, cX, cY, halfDimension, _rcX, _rcY, _rhalfWidth, _rhalfHeight, _rPixelScale);
                    QEntry entryNE = new QEntry(level + 1, cX, cY, halfDimension, node.northEast, benefitNE);
                    queue.add(entryNE);
                    if (node.northEast.sample != null) {
                        availableSampleSize -= Constants.NODE_SAMPLE_SIZE;
                    }
                }

                // southwest
                cX = ncX - halfDimension;
                cY = ncY + halfDimension;
                // ignore this node if the range does not intersect with it
                if (intersectsBBox(cX, cY, halfDimension, _rcX, _rcY, _rhalfWidth, _rhalfHeight)) {
                    double benefitSW = computeBenefit(node.southWest, cX, cY, halfDimension, _rcX, _rcY, _rhalfWidth, _rhalfHeight, _rPixelScale);
                    QEntry entrySW = new QEntry(level + 1, cX, cY, halfDimension, node.southWest, benefitSW);
                    queue.add(entrySW);
                    if (node.southWest.sample != null) {
                        availableSampleSize -= Constants.NODE_SAMPLE_SIZE;
                    }
                }

                // southeast
                cX = ncX + halfDimension;
                cY = ncY + halfDimension;
                // ignore this node if the range does not intersect with it
                if (intersectsBBox(cX, cY, halfDimension, _rcX, _rcY, _rhalfWidth, _rhalfHeight)) {
                    double benefitSE = computeBenefit(node.southEast, cX, cY, halfDimension, _rcX, _rcY, _rhalfWidth, _rhalfHeight, _rPixelScale);
                    QEntry entrySE = new QEntry(level + 1, cX, cY, halfDimension, node.southEast, benefitSE);
                    queue.add(entrySE);
                    if (node.southEast.sample != null) {
                        availableSampleSize -= Constants.NODE_SAMPLE_SIZE;
                    }
                }
            }

            //-DEBUG-//
            System.out.println("[availableSampleSize] = " + availableSampleSize);

            return result;
        }

        /**
         * Post-order traverse the Quadtree,
         * select the best sample for each node
         *
         * V1 - select the best from only its 4 children
         */
        public void selectSamples(double _cX, double _cY, double _halfDimension, int _level) {
            // leaf node already has the best sample
            if (this.northWest == null) {
                return;
            }

            double halfDimension = _halfDimension / 2;

            // select best samples for all four children first
            this.northWest.selectSamples(_cX - halfDimension, _cY - halfDimension, halfDimension, _level + 1);
            this.northEast.selectSamples(_cX + halfDimension, _cY - halfDimension, halfDimension, _level + 1);
            this.southWest.selectSamples(_cX - halfDimension, _cY + halfDimension, halfDimension, _level + 1);
            this.southEast.selectSamples(_cX + halfDimension, _cY + halfDimension, halfDimension, _level + 1);

            // render the four best samples on four children as the ground truth
            byte[] rendering0 = renderer.createRendering(Constants.NODE_RESOLUTION);
            if (this.northWest.sample != null) {
                renderer.render(rendering0, _cX, _cY, _halfDimension, Constants.NODE_RESOLUTION, this.northWest.sample);
            }
            if (this.northEast.sample != null) {
                renderer.render(rendering0, _cX, _cY, _halfDimension, Constants.NODE_RESOLUTION, this.northEast.sample);
            }
            if (this.southWest.sample != null) {
                renderer.render(rendering0, _cX, _cY, _halfDimension, Constants.NODE_RESOLUTION, this.southWest.sample);
            }
            if (this.southEast.sample != null) {
                renderer.render(rendering0, _cX, _cY, _halfDimension, Constants.NODE_RESOLUTION, this.southEast.sample);
            }

            // render each candidate of the four children individually and select the minimum error one
            double minError = Double.MAX_VALUE;
            Point bestSample = null;
            if (this.northWest.sample != null) {
                byte[] renderingNW = renderer.createRendering(Constants.NODE_RESOLUTION);
                renderer.render(renderingNW, _cX, _cY, _halfDimension, Constants.NODE_RESOLUTION, this.northWest.sample);
                double error = errorMetric.error(rendering0, renderingNW, renderer.realResolution(Constants.NODE_RESOLUTION));
                if (error < minError) {
                    minError = error;
                    bestSample = this.northWest.sample;
                }
            }
            if (this.northEast.sample != null) {
                byte[] renderingNE = renderer.createRendering(Constants.NODE_RESOLUTION);
                renderer.render(renderingNE, _cX, _cY, _halfDimension, Constants.NODE_RESOLUTION, this.northEast.sample);
                double error = errorMetric.error(rendering0, renderingNE, renderer.realResolution(Constants.NODE_RESOLUTION));
                if (error < minError) {
                    minError = error;
                    bestSample = this.northEast.sample;
                }
            }
            if (this.southWest.sample != null) {
                byte[] renderingSW = renderer.createRendering(Constants.NODE_RESOLUTION);
                renderer.render(renderingSW, _cX, _cY, _halfDimension, Constants.NODE_RESOLUTION, this.southWest.sample);
                double error = errorMetric.error(rendering0, renderingSW, renderer.realResolution(Constants.NODE_RESOLUTION));
                if (error < minError) {
                    minError = error;
                    bestSample = this.southWest.sample;
                }
            }
            if (this.southEast.sample != null) {
                byte[] renderingSE = renderer.createRendering(Constants.NODE_RESOLUTION);
                renderer.render(renderingSE, _cX, _cY, _halfDimension, Constants.NODE_RESOLUTION, this.southEast.sample);
                double error = errorMetric.error(rendering0, renderingSE, renderer.realResolution(Constants.NODE_RESOLUTION));
                if (error < minError) {
                    minError = error;
                    bestSample = this.southEast.sample;
                }
            }

            this.sample = bestSample;
        }
    }

    QuadTree quadTree;
    int totalNumberOfPoints = 0;
    int totalStoredNumberOfPoints = 0;
    static long nodesCount = 0; // count quad-tree nodes

    /** For query stats */
    static int[] numberOfNodesStoppedAtLevels; // for current query, count how many nodes stopped at a certain level

    /** For query time analysis */
    static Map<String, Double> times; // for current query, store times for different parts

    //-Timing-//
    static final boolean keepTiming = true;
    Map<String, Double> timing;
    //-Timing-//

    public RAQuadTreeV2() {
        this.quadTree = new QuadTree();

        // zoom level 0 is fixed with dimension 1.0
        highestLevelNodeDimension = 1.0 / Math.pow(2, Constants.MAX_ZOOM);

        switch (Constants.RENDERING_FUNCTION.toLowerCase()) {
            case "deckgl":
                System.out.println("[RA-QuadTree] rendering function = Deck.GL");
                renderer =  new DeckGLRenderer(Constants.RADIUS_IN_PIXELS);
                switch (Constants.ERROR_FUNCTION.toLowerCase()) {
                    case "l2":
                        System.out.println("[RA-QuadTree] error function = L2");
                        errorMetric = new L2Error();
                        break;
                    case "l1":
                    default:
                        System.out.println("[RA-QuadTree] error function = L1");
                        errorMetric = new L1Error();
                }
                break;
            case "snap":
            default:
                System.out.println("[RA-QuadTree] rendering function = Snap");
                renderer = new SnapRenderer();
                switch (Constants.ERROR_FUNCTION.toLowerCase()) {
                    case "l2":
                        System.out.println("[RA-QuadTree] error function = Snap L2");
                        errorMetric = new SnapL2Error();
                        break;
                    case "l1":
                    default:
                        System.out.println("[RA-QuadTree] error function = Snap L1");
                        errorMetric = new SnapL1Error();
                }
        }

        // initialize the timing map
        if (keepTiming) {
            timing = new HashMap<>();
            timing.put("total", 0.0);
        }

        /** For query stats */
        numberOfNodesStoppedAtLevels = new int[Constants.MAX_ZOOM + 2];

        /** For query time analysis */
        times = new HashMap<>();

        MyMemory.printMemory();
    }

    public void load(List<Point> points) {
        System.out.println("[RA-QuadTree] loading " + points.size() + " points ... ...");

        MyTimer.startTimer();
        this.totalNumberOfPoints += points.size();
        int count = 0;
        int skip = 0;
        MyTimer.startTimer();
        for (Point point: points) {
            if (this.quadTree.insert(0.5, 0.5, 0.5, lngLatToXY(point), 0))
                count ++;
            else
                skip ++;
        }
        MyTimer.stopTimer();
        double insertTime = MyTimer.durationSeconds();
        this.totalStoredNumberOfPoints += count;
        System.out.println("[RA-QuadTree] inserted " + count + " points and skipped " + skip + " points.");
        System.out.println("[RA-QuadTree] insertion time: " + insertTime + " seconds.");

        // select best sample for each node in the QuadTree
        MyTimer.startTimer();
        this.quadTree.selectSamples(0.5, 0.5, 0.5, 0);
        MyTimer.stopTimer();
        double selectSamplesTime = MyTimer.durationSeconds();
        System.out.println("[RA-QuadTree] select best sample for each node is done!");
        System.out.println("[RA-QuadTree] sample selection time: " + selectSamplesTime + " seconds.");

        MyTimer.stopTimer();
        double loadTime = MyTimer.durationSeconds();

        if (keepTiming) timing.put("total", timing.get("total") + loadTime);
        System.out.println("[RA-QuadTree] loading is done!");
        System.out.println("[RA-QuadTree] loading time: " + loadTime + " seconds.");
        if (keepTiming) this.printTiming();

        MyMemory.printMemory();

        //-DEBUG-//
        System.out.println("==== Until now ====");
        System.out.println("RA-QuadTree has processed " + this.totalNumberOfPoints + " points.");
        System.out.println("RA-QuadTree has stored " + this.totalStoredNumberOfPoints + " points.");
        System.out.println("RA-QuadTree has skipped " + skip + " points.");
        System.out.println("RA-QuadTree has generated " + nodesCount + " nodes.");
        //-DEBUG-//
    }

    @Override
    public void finishLoad() {

    }

    public static double computeBenefit(QuadTree _node, double _ncX, double _ncY, double _nhalfDimension,
                                        double _rcX, double _rcY, double _rhalfWidth, double _rhalfHeight,
                                        double _rPixelScale) {

        //--time--//
        long startTime = System.nanoTime();

        // if already leaf, benefit is 0.0, no need to expand it
        if (_node.northWest == null) return 0.0;

        // get the resolution for given _node as piece of the result
        int resolution = (int) Math.round(2 * _nhalfDimension / _rPixelScale);

        // TODO - verify for DeckGLRenderer
        if (resolution == 0) return 0.0;

        double benefit;

        // resolution > # points to be rendered, use pixel list rendering
        if (resolution > 4 * Constants.NODE_SAMPLE_SIZE) {
            // render the point on node
            // for pixel list rendering, background is always an empty list
            List<Pixel> rendering1 = new ArrayList<>();
            int sampleSize1 = 0;
            if (_node.sample != null) {
                //--time--//
                long startTime1 = System.nanoTime();
                renderer.render(rendering1, _ncX, _ncY, _nhalfDimension, resolution, _node.sample);
                //--time--//
                long endTime1 = System.nanoTime();
                times.put("rendering", times.get("rendering") + ((double) (endTime1 - startTime1) / 1000000000.0));
                sampleSize1 = Constants.NODE_SAMPLE_SIZE;
            }
            // render the 4 children points
            // for pixel list rendering, background is always an empty list
            List<Pixel> rendering2 = new ArrayList<>();
            int sampleSize2 = 0;
            if (_node.northWest.sample != null) {
                //--time--//
                long startTime1 = System.nanoTime();
                renderer.render(rendering2, _ncX, _ncY, _nhalfDimension, resolution, _node.northWest.sample);
                //--time--//
                long endTime1 = System.nanoTime();
                times.put("rendering", times.get("rendering") + ((double) (endTime1 - startTime1) / 1000000000.0));
                sampleSize2 += Constants.NODE_SAMPLE_SIZE;
            }
            if (_node.northEast.sample != null) {
                //--time--//
                long startTime1 = System.nanoTime();
                renderer.render(rendering2, _ncX, _ncY, _nhalfDimension, resolution, _node.northEast.sample);
                //--time--//
                long endTime1 = System.nanoTime();
                times.put("rendering", times.get("rendering") + ((double) (endTime1 - startTime1) / 1000000000.0));
                sampleSize2 += Constants.NODE_SAMPLE_SIZE;
            }
            if (_node.southWest.sample != null) {
                //--time--//
                long startTime1 = System.nanoTime();
                renderer.render(rendering2, _ncX, _ncY, _nhalfDimension, resolution, _node.southWest.sample);
                //--time--//
                long endTime1 = System.nanoTime();
                times.put("rendering", times.get("rendering") + ((double) (endTime1 - startTime1) / 1000000000.0));
                sampleSize2 += Constants.NODE_SAMPLE_SIZE;
            }
            if (_node.southEast.sample != null) {
                //--time--//
                long startTime1 = System.nanoTime();
                renderer.render(rendering2, _ncX, _ncY, _nhalfDimension, resolution, _node.southEast.sample);
                //--time--//
                long endTime1 = System.nanoTime();
                times.put("rendering", times.get("rendering") + ((double) (endTime1 - startTime1) / 1000000000.0));
                sampleSize2 += Constants.NODE_SAMPLE_SIZE;
            }
            //--time--//
            long startTime1e = System.nanoTime();
            // gain
            double gain = errorMetric.error(rendering1, rendering2, renderer.realResolution(resolution));
            //--time--//
            long endTime1e = System.nanoTime();
            times.put("error", times.get("error") + ((double) (endTime1e - startTime1e) / 1000000000.0));
            // cost
            int cost = sampleSize2 - sampleSize1;
            if (cost == 0) {
                benefit = Double.MAX_VALUE;
            }
            else {
                benefit = gain / (double) cost;
            }

            //-DEBUG-//
            if (benefit <= 0.0) {
                System.out.println("[computeBenefit] using List<Pixel> for rendering and error.");
                System.out.println("[computeBenefit] benefit = 0.0.");
                System.out.println("[computeBenefit] gain = " + gain);
                System.out.println("[computeBenefit] cost = " + cost);
            }
        }
        // otherwise, use byte array rendering
        else {
            // render the point on node
            byte[] rendering1 = renderer.createRendering(resolution);
            int sampleSize1 = 0;
            if (_node.sample != null) {
                //--time--//
                long startTime2 = System.nanoTime();
                renderer.render(rendering1, _ncX, _ncY, _nhalfDimension, resolution, _node.sample);
                //--time--//
                long endTime2 = System.nanoTime();
                times.put("rendering", times.get("rendering") + ((double) (endTime2 - startTime2) / 1000000000.0));
                sampleSize1 = Constants.NODE_SAMPLE_SIZE;
            }
            // render the 4 children points
            byte[] rendering2 = renderer.createRendering(resolution);
            int sampleSize2 = 0;
            if (_node.northWest.sample != null) {
                //--time--//
                long startTime2 = System.nanoTime();
                renderer.render(rendering2, _ncX, _ncY, _nhalfDimension, resolution, _node.northWest.sample);
                //--time--//
                long endTime2 = System.nanoTime();
                times.put("rendering", times.get("rendering") + ((double) (endTime2 - startTime2) / 1000000000.0));
                sampleSize2 += Constants.NODE_SAMPLE_SIZE;
            }
            if (_node.northEast.sample != null) {
                //--time--//
                long startTime2 = System.nanoTime();
                renderer.render(rendering2, _ncX, _ncY, _nhalfDimension, resolution, _node.northEast.sample);
                //--time--//
                long endTime2 = System.nanoTime();
                times.put("rendering", times.get("rendering") + ((double) (endTime2 - startTime2) / 1000000000.0));
                sampleSize2 += Constants.NODE_SAMPLE_SIZE;
            }
            if (_node.southWest.sample != null) {
                //--time--//
                long startTime2 = System.nanoTime();
                renderer.render(rendering2, _ncX, _ncY, _nhalfDimension, resolution, _node.southWest.sample);
                //--time--//
                long endTime2 = System.nanoTime();
                times.put("rendering", times.get("rendering") + ((double) (endTime2 - startTime2) / 1000000000.0));
                sampleSize2 += Constants.NODE_SAMPLE_SIZE;
            }
            if (_node.southEast.sample != null) {
                //--time--//
                long startTime2 = System.nanoTime();
                renderer.render(rendering2, _ncX, _ncY, _nhalfDimension, resolution, _node.southEast.sample);
                //--time--//
                long endTime2 = System.nanoTime();
                times.put("rendering", times.get("rendering") + ((double) (endTime2 - startTime2) / 1000000000.0));
                sampleSize2 += Constants.NODE_SAMPLE_SIZE;
            }
            //--time--//
            long startTime2e = System.nanoTime();
            // gain
            double gain = errorMetric.error(rendering1, rendering2, renderer.realResolution(resolution));
            //--time--//
            long endTime2e = System.nanoTime();
            times.put("error", times.get("error") + ((double) (endTime2e - startTime2e) / 1000000000.0));
            // cost
            int cost = sampleSize2 - sampleSize1;
            if (cost == 0) {
                benefit = Double.MAX_VALUE;
            }
            else {
                benefit = gain / (double) cost;
            }

            //-DEBUG-//
            if (benefit <= 0.0) {
                System.out.println("[byte array] benefit = 0.0.");
                System.out.println("[byte array] gain = " + gain);
                System.out.println("[byte array] cost = " + cost);
                System.out.println("[byte array] resolution = " + resolution);
                //printRenderingGray("rendering1", rendering1, resolution, true);
                //printRenderingGray("rendering2", rendering2, resolution, true);
            }
        }

        //--time--//
        long endTime = System.nanoTime();
        times.put("computeBenefit", times.get("computeBenefit") + ((double) (endTime - startTime) / 1000000000.0));

        return benefit;
    }

    public byte[] answerQuery(Query query) {
        double lng0 = query.bbox[0];
        double lat0 = query.bbox[1];
        double lng1 = query.bbox[2];
        double lat1 = query.bbox[3];
        int resX = query.resX;
        int resY = query.resY;
        int zoom = query.zoom;
        int sampleSize = query.sampleSize <= 0? Constants.DEFAULT_SAMPLE_SIZE: query.sampleSize;

        MyTimer.startTimer();
        System.out.println("[RA-QuadTree] is answering query: \n" +
                "Q = { \n" +
                "    range: [" + lng0 + ", " + lat0 + "] ~ [" + lng1 + ", " + lat1 + "], \n" +
                "    resolution: [" + resX + " x " + resY + "], \n" +
                "    zoom: " + zoom + ",\n " +
                "    sampleSize: " + sampleSize + " \n" +
                " }");

        double iX0 = lngX(lng0);
        double iY0 = latY(lat0);
        double iX1 = lngX(lng1);
        double iY1 = latY(lat1);
        //TODO - verify this 256
        double pixelScale = 1.0 / 256 / Math.pow(2, zoom);
        double rcX = (iX0 + iX1) / 2;
        double rcY = (iY0 + iY1) / 2;
        double rhalfWidth = (iX1 - iX0) / 2;
        double rhalfHeight = (iY0 - iY1) / 2;

        System.out.println("[RA-QuadTree] starting range search on QuadTree with: \n" +
                "bbox = [(" + iX0 + ", " + iY0 + "), (" + iX1 + ", " + iY1 + ")] ; \n" +
                "range = [(" + rcX + ", " + rcY + "), " + rhalfWidth + ", " + rhalfHeight + "] ; \n" +
                "pixelScale = " + pixelScale + ";");

        /** For query stats*/
        for (int i = 0; i <= Constants.MAX_ZOOM; i ++) numberOfNodesStoppedAtLevels[i] = 0;

        /** For query time analysis */
        times.put("computeBenefit", 0.0);
        times.put("rendering", 0.0);
        times.put("error", 0.0);

        MyTimer.startTimer();
        System.out.println("[RA-QuadTree] is doing a best first search with sampleSize = " + sampleSize + ".");
        List<Point> points = this.quadTree.bfs(0.5, 0.5, 0.5,
                rcX, rcY, rhalfWidth, rhalfHeight, pixelScale, sampleSize);
        MyTimer.stopTimer();
        double treeTime = MyTimer.durationSeconds();

        MyTimer.temporaryTimer.put("treeTime", treeTime);
        System.out.println("[RA-QuadTree] tree search got " + points.size() + " data points.");
        System.out.println("[RA-QuadTree] tree search time: " + treeTime + " seconds.");
        System.out.println("[RA-QuadTree]     - compute benefit time: " + times.get("computeBenefit") + " seconds.");
        System.out.println("[RA-QuadTree]         - rendering time: " + times.get("rendering") + " seconds.");
        System.out.println("[RA-QuadTree]         - compute error time: " + times.get("error") + " seconds.");

        // build binary result message
        MyTimer.startTimer();
        BinaryMessageBuilder messageBuilder = new BinaryMessageBuilder();
        double lng, lat;
        int resultSize = 0;
        for (Point point : points) {
            lng = xLng(point.getX());
            lat = yLat(point.getY());
            messageBuilder.add(lng, lat);
            resultSize++;
        }
        MyTimer.stopTimer();
        double buildBinaryTime = MyTimer.durationSeconds();
        MyTimer.temporaryTimer.put("aggregateTime", buildBinaryTime);

        System.out.println("[RA-QuadTree] build binary result with  " + resultSize + " points.");
        System.out.println("[RA-QuadTree] build binary result time: " + buildBinaryTime + " seconds.");

        MyTimer.stopTimer();
        System.out.println("[RA-QuadTree] answer query total time: " + MyTimer.durationSeconds() + " seconds.");
        System.out.println("[RA-QuadTree] ---- # of nodes stopping at each level ----");
        for (int i = 0; i <= Constants.MAX_ZOOM; i ++) {
            System.out.println("Level " + i + ": " + numberOfNodesStoppedAtLevels[i]);
        }

        return messageBuilder.getBuffer();
    }

    @Override
    public boolean readFromFile(String fileName) {
        return false;
    }

    @Override
    public boolean writeToFile(String fileName) {
        return false;
    }

    private void printTiming() {
        System.out.println("[Total Time] " + timing.get("total") + " seconds.");
    }

    public static void printRenderingGray(String name, byte[] _rendering, int _resolution, boolean _expansion) {
        int side = _expansion? _resolution + 2 * (Constants.RADIUS_IN_PIXELS + 1): _resolution;
        System.out.println("========== " + name + "==========");
        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                int r = UnsignedByte.toInt(_rendering[i * side * 3 + j * 3 + 0]);
                int g = UnsignedByte.toInt(_rendering[i * side * 3 + j * 3 + 1]);
                int b = UnsignedByte.toInt(_rendering[i * side * 3 + j * 3 + 2]);
                // gray scaling formula = (0.3 * R) + (0.59 * G) + (0.11 * B)
                int gray = (int) ((0.3 * r) + (0.59 * g) + (0.11 * b));
                if (j > 0) System.out.print(" ");
                System.out.print(gray);
            }
            System.out.println();
        }
    }
}
