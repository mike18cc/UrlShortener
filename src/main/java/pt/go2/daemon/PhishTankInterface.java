package pt.go2.daemon;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.http.TruncatedChunkException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pt.go2.fileio.Configuration;
import pt.go2.keystore.Uri;

/**
 * Downloads file from PhishTank API
 */
public class PhishTankInterface implements WatchDogTask {

	private final static Logger logger = LogManager
			.getLogger(PhishTankInterface.class);

	// watchdog sleep time

	private final long WATCHDOG_SLEEP_MINUTES = 16;

	// expected entries on API - used to avoid resizing in loop

	private final int EXPECTED_ENTRIES = 15000;

	// keep the ids off all known entries - supplied by PhishTank
	// and all the banned Uris

	private Set<Integer> ids = new HashSet<>(0);
	private volatile Set<Uri> banned = new HashSet<>(0);

	// last time the list was refreshed successfully

	private volatile Date lastDownload;

	// url to fetch list from, needs api-key from configuration

	private final String API_URL;

	/**
	 * Factory method - only creates instance if api-key is in configuration
	 * 
	 * @param config
	 * @return
	 */
	public static PhishTankInterface create(Configuration config) {

		if (config.PHISHTANK_API_KEY == null) {
			return null;
		}

		return new PhishTankInterface(config.PHISHTANK_API_KEY);
	}

	/**
	 * Check if Uri is banned
	 * 
	 * @param uri
	 * @return
	 */
	public boolean isBanned(final Uri uri) {
		return banned.contains(uri);
	}

	/**
	 * Refresh banned list
	 * 
	 * TODO: check existing URLS against updated list
	 */
	@Override
	public void refresh() {

		List<Uri> updated = null;

		try {
			updated = download();
		} catch (IOException e) {
			logger.warn("Issues found when using remote FishTank API.");
		}

		if (updated != null) {
			lastDownload = Calendar.getInstance().getTime();
		}
	}

	/**
	 * Call API and parse response
	 * 
	 * @return
	 * 
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws TruncatedChunkException
	 */
	private List<Uri> download() throws ClientProtocolException, IOException,
			TruncatedChunkException {

		logger.info("Download starting");

		final CloseableHttpClient httpclient = HttpClients.createDefault();

		final HttpGet httpGet = new HttpGet(API_URL);

		final CloseableHttpResponse response = httpclient.execute(httpGet);

		final Set<Integer> ids = new HashSet<>(EXPECTED_ENTRIES);
		final Set<Uri> banned = new HashSet<>(EXPECTED_ENTRIES);
		final List<Uri> update = new ArrayList<>();

		long refused = 0;

		try {

			final int statusCode = response.getStatusLine().getStatusCode();

			if (statusCode != 200) {
				logger.error("Error on download: " + statusCode);
				return null;
			}

			final BufferedReader br = new BufferedReader(new InputStreamReader(
					(response.getEntity().getContent())));

			String entry;
			br.readLine(); // skip header
			while ((entry = br.readLine()) != null) {

				final int i = entry.indexOf(',');

				final int urlId;
				try {
					urlId = Integer.parseInt(entry.substring(0, i));
				} catch (NumberFormatException nfe) {
					logger.error("Error parsing: " + entry);
					continue;
				}

				int idx = i + 1, end;

				if (entry.charAt(idx) == '"') {
					idx++;
					end = entry.indexOf('"', idx);
				} else {
					end = entry.indexOf(',', idx);
				}

				final Uri uri;				
				uri = Uri.create(entry.substring(idx, end), false);

				if (banned.add(uri)) {

					if (!this.ids.contains(urlId)) {

						update.add(uri);
					}
					ids.add(urlId);

				} else {
					refused++;
				}
			}

			logger.info("Stats - Old: " + this.banned.size() + " Now: "
					+ banned.size() + " New: " + update.size() + " Refused: "
					+ refused);

			this.banned = banned;
			this.ids = ids;

			
		} finally {
			response.close();
		}

		logger.info("Download exiting");

		return update;
	}

	/**
	 * C'tor use factory method instead
	 * 
	 * @param apiKey
	 */
	private PhishTankInterface(final String apiKey) {

		API_URL = "http://data.phishtank.com/data/" + apiKey
				+ "/online-valid.csv";
	}

	@Override
	public Date lastRun() {
		return lastDownload;
	}

	@Override
	public long interval() {
		return WATCHDOG_SLEEP_MINUTES;
	}
}