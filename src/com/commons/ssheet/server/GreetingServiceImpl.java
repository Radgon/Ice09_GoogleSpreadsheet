package com.commons.ssheet.server;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.PersistenceException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.JpaCallback;
import org.springframework.orm.jpa.JpaTemplate;
import org.springframework.stereotype.Component;

import com.commons.ssheet.client.GreetingService;
import com.google.gdata.client.http.GoogleGDataRequest;
import com.google.gdata.client.spreadsheet.SpreadsheetService;
import com.google.gdata.data.spreadsheet.CellEntry;
import com.google.gdata.data.spreadsheet.CellFeed;
import com.google.gdata.data.spreadsheet.SpreadsheetEntry;
import com.google.gdata.data.spreadsheet.SpreadsheetFeed;
import com.google.gdata.data.spreadsheet.WorksheetEntry;
import com.google.gdata.util.ServiceException;
import com.google.gwt.user.server.rpc.RemoteServiceServlet;

@SuppressWarnings("serial")
@Component
public class GreetingServiceImpl extends RemoteServiceServlet implements GreetingService {

 	static {
		System.setProperty(GoogleGDataRequest.DISABLE_COOKIE_HANDLER_PROPERTY, "true");
 	}
	
	private boolean first;

	@Autowired
	private EntityManager entityManager;
	@Autowired
	private SpreadsheetService spreadsheetService;
	@Autowired
	private URL metafeedUrl;
	
	private String username = null;
	private String password = null;
	
	public JpaTemplate getTemplate() {
		return new JpaTemplate(entityManager);
	}
	
	public String greetServer(String input) {
		try {
			return getData(input);
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ServiceException e) {
			e.printStackTrace();
		}
		return null;
	}

	private String getData(String input) throws IOException, ServiceException {
		String all = "";
		if (input.equals("sync")) {
			first = false;
		}
		for (Customer customer : getCustomers()) {
			if (!first) {
				removeCustomer(customer);
			}
		}
		first = true;
		persistCustomer(createCustomer(input));
		if (input.equals("sync")) {
			for (Customer customer : syncWithSpredsheet()) {
				persistCustomer(customer);
			}
		}
		for (Customer customer : getCustomers()) {
			all += "id: " + customer.getId() + " - firstname: " + customer.getFirstName() + " - name:" + customer.getLastName() + "<br>";
		}
		return all;
	}

	private void removeCustomer(Customer customer) {
		EntityTransaction trx = entityManager.getTransaction();
		trx.begin();
		entityManager.remove(customer);
		trx.commit();
	}

	private Customer createCustomer(String input) {
		Customer newCustomer = new Customer();
		newCustomer.setFirstName(input + System.currentTimeMillis());
		newCustomer.setLastName(input + System.currentTimeMillis());
		return newCustomer;
	}
	
	private void persistCustomer(Customer customer) {
		EntityTransaction trx = entityManager.getTransaction();
		trx.begin();
		entityManager.persist(customer);
		trx.commit();
	}

	private Collection<Customer> getCustomers() {
		return getTemplate().execute(new JpaCallback<Collection<Customer>>() {
			@Override
			public Collection<Customer> doInJpa(EntityManager arg0) throws PersistenceException {
				return (Collection<Customer>) entityManager.createQuery("SELECT cust FROM com.commons.ssheet.server.Customer cust").getResultList();
			}
		});
	}
	
	private List<Customer> syncWithSpredsheet() throws IOException, ServiceException {
		spreadsheetService.setUserCredentials(username, password);
		List<Customer> result = new ArrayList<Customer>();
		SpreadsheetFeed feed = (SpreadsheetFeed) spreadsheetService.getFeed(metafeedUrl, SpreadsheetFeed.class);
		List<SpreadsheetEntry> spreadsheets = feed.getEntries();
		for (int i = 0; i < spreadsheets.size(); i++) {
			SpreadsheetEntry entry = spreadsheets.get(i);
			if (entry.getTitle().getPlainText().trim().equals("tabledata")) {
				List<WorksheetEntry> worksheets = entry.getWorksheets();
				for (int j = 0; j < worksheets.size(); j++) {
					WorksheetEntry worksheet = worksheets.get(j);
					URL cellFeedUrl = worksheet.getCellFeedUrl();
					CellFeed cfeed = spreadsheetService.getFeed(cellFeedUrl, CellFeed.class);
					Customer customer = new Customer();
					boolean wasAdded = true;
					for (CellEntry cell : cfeed.getEntries()) {
						String value = cell.getCell().getInputValue();
						if (cell.getTitle().getPlainText().startsWith("A")) {
							if (!wasAdded) {
								result.add(customer);
								customer = new Customer();
							}
							customer.setFirstName(value);
						} else if (cell.getTitle().getPlainText().startsWith("B")) {
							customer.setLastName(value);
							wasAdded = false;
						}
					}
					result.add(customer);
				}
			}
		}
		return result;
	}

}
