package software.reliabletx.camel;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import junit.framework.TestCase;

public abstract class SpringTestCase extends TestCase {
    private ClassPathXmlApplicationContext springContext;

    public void setUpSpring(String springResourcesFile) {
        /* Initialize a Spring context using an application context
         * configured from a config file that configures a JTA transaction
         * manager. */
        this.springContext = new ClassPathXmlApplicationContext(springResourcesFile);
    }

    public ApplicationContext getSpringContext() {
        assertNotNull(springContext);
        return springContext;
    }

    public void stopSpringContext() {
        assertNotNull(springContext);
        try {
            springContext.close();
        } finally {
            springContext.stop();
        }
    }

    public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
        assertNotNull(springContext);
        return springContext.getBean(name, requiredType);
    }
}
