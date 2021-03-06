package net.explorviz.server.main;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.hibernate.Session;

import net.explorviz.server.repository.HibernateSessionFactory;
import net.explorviz.server.repository.LandscapeExchangeService;
import net.explorviz.server.security.PasswordStorage;
import net.explorviz.server.security.PasswordStorage.CannotPerformOperationException;
import net.explorviz.server.security.User;

@WebListener
public class SetupListener implements ServletContextListener {

	@Override
	public void contextInitialized(final ServletContextEvent servletContextEvent) {

		String hashedPassword = "";
		try {
			hashedPassword = PasswordStorage.createHash("admin");
		} catch (final CannotPerformOperationException e) {
			System.err.println("Couldn't create default admin : " + e);
			return;
		}

		final HibernateSessionFactory sessionFactory = new HibernateSessionFactory(
				servletContextEvent.getServletContext());

		final Session session = sessionFactory.beginTransaction();
		session.save(new User("admin", hashedPassword));
		sessionFactory.commitTransactionAndClose(session);

		// Start ExplorViz Listener
		LandscapeExchangeService.startRepository();
		System.out.println("* * * * * * * * * * * * * * * * * * *\n");
		System.out.println("Server started. Traces should be processed. (Start Test App now)\n");
		System.out.println("* * * * * * * * * * * * * * * * * * *");

	}

	@Override
	public void contextDestroyed(final ServletContextEvent servletContextEvent) {
		// Nothing to dispose
	}

}
