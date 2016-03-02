package macrobase.analysis.outlier;

import macrobase.analysis.BaseAnalyzer;
import macrobase.conf.ConfigurationException;
import macrobase.conf.MacroBaseConf;
import macrobase.datamodel.Datum;

import macrobase.ingest.CsvLoader;
import macrobase.ingest.DataLoader;
import macrobase.ingest.DatumEncoder;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertEquals;

public class KDETest {

    @Test
    public void simpleTest() {
        MacroBaseConf conf = new MacroBaseConf()
                .set(MacroBaseConf.KDE_KERNEL_TYPE, "EPANECHNIKOV_MULTIPLICATIVE")
                .set(MacroBaseConf.KDE_BANDWIDTH_ALGORITHM, "OVERSMOOTHED");
        KDE kde = new KDE(conf);
        kde.setProportionOfDataToUse(1.0);
        List<Datum> data = new ArrayList<>();
        for (int i = 0; i < 100; ++i) {
            double[] sample = new double[1];
            sample[0] = i;
            data.add(new Datum(new ArrayList<>(), new ArrayRealVector(sample)));
        }

        kde.train(data);
        assertEquals(kde.score(data.get(0)), -0.00513, 1e-5);
        assertEquals(kde.score(data.get(50)), -0.009997, 1e-5);
        assertEquals(kde.score(data.get(data.size() - 1)), -0.005132, 1e-5);
    }

    @Test
    public void StandardNormal2DTest() throws ConfigurationException, IOException, SQLException {
        MacroBaseConf conf = new MacroBaseConf()
                .set(MacroBaseConf.DETECTOR_TYPE, "KDE")
                .set(MacroBaseConf.KDE_KERNEL_TYPE, "EPANECHNIKOV_MULTIPLICATIVE")
                .set(MacroBaseConf.KDE_BANDWIDTH_ALGORITHM, "NORMAL_SCALE")
                .set(MacroBaseConf.DATA_LOADER_TYPE, "CSV_LOADER")
                .set(MacroBaseConf.CSV_COMPRESSION, CsvLoader.Compression.GZIP)
                .set(MacroBaseConf.CSV_INPUT_FILE, "src/test/resources/data/2d_standard_normal_100k.csv.gz")
                .set(MacroBaseConf.HIGH_METRICS, "XX, YY")
                .set(MacroBaseConf.LOW_METRICS, "")
                .set(MacroBaseConf.ATTRIBUTES, "")
                .set(MacroBaseConf.DATA_TRANSFORM_TYPE, "IDENTITY");

        BaseAnalyzer analyzer = new BaseAnalyzer(conf);

        DataLoader loader = analyzer.constructLoader();
        List<Datum> data = loader.getData(new DatumEncoder());

        KDE kde = new KDE(conf);
        kde.setProportionOfDataToUse(1.0);

        assertEquals(100000, data.size());
        kde.train(data);

        double[][] candidates = {
                {0, 0},
                {1, 1},
                {-1, 1},
                {0.2, 0.7},
                {0.8, 0.7},
                {0.4, 0.1},
                {0, 2},
                {0, 0.7},
                {-1, -1},
                {1, -1},
                {0.5, 0.5},
                {20, 20},
        };

        double gaussianNorm = Math.pow(2 * Math.PI, -0.5 * candidates[0].length);
        List<Integer> dummyAttr = new ArrayList<>();

        for (double[] array : candidates) {
            RealVector vector = new ArrayRealVector(array);
            double expectedScore = gaussianNorm * Math.pow(Math.E, -0.5 * vector.getNorm());
            // Accept 10x error or 1e-4 since we don't care about absolute performance only order of magnitude.
            double error = Math.max(1e-4, expectedScore * 0.9);
            double score = -kde.score(new Datum(dummyAttr, vector));

            System.out.println(vector);
            assertTrue(String.format("expected abs(%f - %f) < %f", expectedScore, score, error),
                       Math.abs(expectedScore - score) < error);
        }
    }

}
