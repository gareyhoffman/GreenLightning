package com.ociweb.gl.api;

import com.ociweb.pronghorn.network.TLSCertificates;

public interface HTTPServerConfig {
	HTTPServerConfig setDefaultPath(String defaultPath);
	HTTPServerConfig setHost(String host);
	HTTPServerConfig setTLS(TLSCertificates certificates);
	HTTPServerConfig setMaxConnectionBits(int bits);
	HTTPServerConfig useInsecureServer();
	HTTPServerConfig setEncryptionUnitsPerTrack(int value);
	HTTPServerConfig setDecryptionUnitsPerTrack(int value);
	HTTPServerConfig setConcurrentChannelsPerEncryptUnit(int value);
	HTTPServerConfig setConcurrentChannelsPerDecryptUnit(int value);

	int getMaxConnectionBits();

	int getEncryptionUnitsPerTrack();

	int getDecryptionUnitsPerTrack();

	int getConcurrentChannelsPerEncryptUnit();

	int getConcurrentChannelsPerDecryptUnit();

	boolean isTLS();

	TLSCertificates getCertificates();

	String bindHost();

	int bindPort();

	String defaultHostPath();
}

