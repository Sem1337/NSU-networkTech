import main.proxy.Proxy;

import java.io.IOException;

public class Application {

    public static void main(String[] args) {

        try {
            new Proxy(1337).start();
        } catch (IOException e) {
            System.out.println(e.getLocalizedMessage());
        }
    }
}
