package com.commons.ssheet.server;

import java.util.Collection;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.PersistenceException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.jpa.JpaCallback;
import org.springframework.orm.jpa.JpaTemplate;
import org.springframework.stereotype.Component;

import com.commons.ssheet.client.GreetingService;
import com.google.gwt.user.server.rpc.RemoteServiceServlet;

@SuppressWarnings("serial")
@Component
public class GreetingServiceImpl extends RemoteServiceServlet implements GreetingService {

 	private boolean first;
	protected EntityManager entityManager;
	
	@Autowired
	public void setEntityManager(EntityManager entityManager) {
		this.entityManager = entityManager;
	}
	
	public JpaTemplate getTemplate() {
		return new JpaTemplate(entityManager);
	}
	
	public String greetServer(String input) {
		return getData(input);
	}

	private String getData(String input) {
		String all = "";
		for (Customer customer : getCustomers()) {
			if (!first) {
				removeCustomer(customer);
			}
		}
		first = true;
		for (Customer customer : getCustomers()) {
			all += "id: " + customer.getId() + " - firstname: " + customer.getFirstName() + " - name:" + customer.getLastName();
		}
		createCustomer(input);
		return all;
	}

	private void removeCustomer(Customer customer) {
		EntityTransaction trx = entityManager.getTransaction();
		trx.begin();
		entityManager.remove(customer);
		trx.commit();
	}

	private void createCustomer(String input) {
		EntityTransaction trx = entityManager.getTransaction();
		Customer newCustomer = new Customer();
		newCustomer.setFirstName(input + System.currentTimeMillis());
		newCustomer.setLastName(input + System.currentTimeMillis());
		trx.begin();
		entityManager.persist(newCustomer);
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

}
