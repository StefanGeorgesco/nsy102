package fr.sgo.model;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

import fr.sgo.entity.Chat;
import fr.sgo.entity.Correspondent;
import fr.sgo.entity.CorrespondentChat;
import fr.sgo.entity.GroupChat;
import fr.sgo.entity.RemoteGroupChat;
import fr.sgo.service.CorrespondentServiceInfo;
import fr.sgo.service.CorrespondentServiceLocator;
import fr.sgo.service.ProfileInfo;
import fr.sgo.service.Storage;

/**
 * Class CorrespondentManager
 * 
 * Manages correspondents
 *
 * @author Stéfan Georgesco
 * @version 1.0
 */
@SuppressWarnings("deprecation")
public class CorrespondentManager extends Observable implements Observer {
	private static CorrespondentManager instance = null;
	private Map<String, Correspondent> correspondents;
	private static String objectName;

	private CorrespondentManager() {
		this.correspondents = Collections.synchronizedMap(new HashMap<String, Correspondent>());
		objectName = "corr_" + ProfileInfo.getInstance().getUserId();
	}

	public static synchronized CorrespondentManager getInstance() {
		if (instance == null)
			instance = new CorrespondentManager();
		return instance;
	}

	public void start() {
		@SuppressWarnings("unchecked")
		Collection<Correspondent> pairedCorrespondents = (Collection<Correspondent>) Storage.restore(objectName);
		if (pairedCorrespondents != null) {
			ChatManager chatManager = ChatManager.getInstance();
			for (Correspondent correspondent : pairedCorrespondents) {
				correspondent.setOnline(false);
				correspondents.put(correspondent.getUserId(), correspondent);
				for (Chat chat : chatManager.getChats()) {
					if (chat instanceof CorrespondentChat && ((CorrespondentChat) chat).getCorrespondent().getUserId()
							.equals(correspondent.getUserId()))
						((CorrespondentChat) chat).setCorrespondent(correspondent);
					if (chat instanceof RemoteGroupChat && ((RemoteGroupChat) chat).getCorrespondent().getUserId()
							.equals(correspondent.getUserId()))
						((RemoteGroupChat) chat).setCorrespondent(correspondent);
					if (chat instanceof GroupChat) {
						Collection<Correspondent> chatCorrespondents = new HashSet<Correspondent>(
								((GroupChat) chat).getCorrespondents());
						for (Correspondent c : chatCorrespondents) {
							if (c.getUserId().equals(correspondent.getUserId()))
								((GroupChat) chat).replaceCorrespondent(c, correspondent);
						}
					}
				}
				setChanged();
				notifyObservers(correspondent);
			}
		}
	}

	public Collection<Correspondent> getCorrespondents() {
		return correspondents.values();
	}

	public Collection<Correspondent> getPairedCorrespondents() {
		Collection<Correspondent> collection = new HashSet<Correspondent>();
		for (Correspondent c : getCorrespondents()) {
			if (c.isPaired())
				collection.add(c);
		}
		return collection;
	}

	public Collection<Correspondent> getUnpairedCorrespondents() {
		Collection<Correspondent> collection = new HashSet<Correspondent>();
		for (Correspondent c : correspondents.values()) {
			if (!c.isPaired())
				collection.add(c);
		}
		return collection;
	}

	public Correspondent getCorrespondent(String userId) {
		return correspondents.get(userId);
	}

	public boolean existsCorrespondent(Correspondent correspondent) {
		return correspondents.values().contains(correspondent);
	}

	public void add(Correspondent correspondent) {
		correspondents.put(correspondent.getUserId(), correspondent);
		reportChange(correspondent);
	}

	public void reportChange(Correspondent correspondent) {
		if (correspondent.isPaired()) {
			Storage.save(getPairedCorrespondents(), objectName);
		}
		setChanged();
		notifyObservers(correspondent);
	}

	public void update(Observable observable, Object arg) {
		new Thread() {
			@Override
			public void run() {
				CorrespondentServiceLocator correspondentServiceLocator = (CorrespondentServiceLocator) observable;
				CorrespondentServiceInfo correspondentServiceInfo = (CorrespondentServiceInfo) arg;
				if (correspondentServiceInfo != null) { // got info
					String userId = correspondentServiceInfo.getUserId();
					assert userId != null; // DEBUG
					String myUserId = ProfileInfo.getInstance().getUserId();
					if (!userId.equals(myUserId)) { // not me
						Correspondent correspondent = correspondents.get(userId); // get correspondent if present
						if (correspondentServiceLocator.lookup(userId) == null) { // service removed
							if (correspondent != null) { // present
								if (correspondent.isPaired()) { // paired
									correspondent.setOnline(false); // set offline
								} else { // unpaired
									correspondents.remove(correspondent.getUserId()); // remove
								}
							}
						} else { // service resolved
							if (correspondent != null) { // present
								correspondent.setUserName(correspondentServiceInfo.getUserName()); // update name
								correspondent.setOnline(true); // set online
							} else { // absent
								// new online unpaired correspondent
								correspondent = new Correspondent(userId, correspondentServiceInfo.getUserName(), true);
								correspondents.put(userId, correspondent);
							}
						}
						setChanged();
						notifyObservers(correspondent);
					}
				}
			}
		}.start();
	}

}
