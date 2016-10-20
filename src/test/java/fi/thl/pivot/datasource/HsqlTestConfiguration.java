package fi.thl.pivot.datasource;

import java.io.IOException;
import java.util.Properties;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.PropertiesFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;

public class HsqlTestConfiguration {

    @Bean
    @Qualifier("queries")
    public Properties myProperties() {
        PropertiesFactoryBean propertiesFactoryBean = new PropertiesFactoryBean();
        propertiesFactoryBean.setLocation(new ClassPathResource("/sql.xml"));
        Properties properties = null;
        try {
            propertiesFactoryBean.afterPropertiesSet();
            properties = propertiesFactoryBean.getObject();

        } catch (IOException e) {
            System.err.println("Cannot load properties file.");
        }
        return properties;
    }

    @Bean
    public DataSource dataSource() {
        return new EmbeddedDatabaseBuilder().addScript("classpath:create-table-hsql.sql").build();
    }

    @Bean
    @Autowired
    public JdbcTemplate template(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public AmorDao dao() {
        return new AmorDao();
    }

}