import deltix.qsrv.hf.tickdb.pub.DXTickDB;
import deltix.qsrv.hf.tickdb.pub.TickDBFactory;

public class TestClient {
    public static void main(String ...args) {
        DXTickDB client = TickDBFactory.createFromUrl("dxctick://localhost:8011");
    }
}
