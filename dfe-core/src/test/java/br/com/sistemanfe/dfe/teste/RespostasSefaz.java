package br.com.sistemanfe.dfe.teste;

/** Respostas de exemplo no formato devolvido pela Sefaz RS. */
public final class RespostasSefaz {

    private RespostasSefaz() {
    }

    public static final String STATUS_107 = """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" \
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
              <soap:Body>
                <nfeResultMsg xmlns="http://www.portalfiscal.inf.br/nfe/wsdl/NFeStatusServico4">
                  <retConsStatServ versao="4.00" xmlns="http://www.portalfiscal.inf.br/nfe">
                    <tpAmb>2</tpAmb><verAplic>RS202611</verAplic><cStat>107</cStat>
                    <xMotivo>Servico em Operacao</xMotivo><cUF>43</cUF>
                    <dhRecbto>2026-11-27T10:00:00-03:00</dhRecbto><tMed>1</tMed>
                  </retConsStatServ>
                </nfeResultMsg>
              </soap:Body>
            </soap:Envelope>
            """;

    // Chaves fictícias (CNPJ 11.222.333/0001-81, RS, 11/2026, série 1), com dígito verificador correto.
    public static final String CHAVE_AUTORIZADA = "43261111222333000181550010000001231876543212";
    public static final String CHAVE_CANCELADA = "43261111222333000181550010000001241564738295";
    public static final String CHAVE_INEXISTENTE = "43261111222333000181550010000009991314159268";

    /** Consulta protocolo de uma NF-e autorizada: traz o {@code protNFe}. */
    public static final String CONSULTA_AUTORIZADA = """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" \
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:xsd="http://www.w3.org/2001/XMLSchema">
              <soap:Body>
                <nfeResultMsg xmlns="http://www.portalfiscal.inf.br/nfe/wsdl/NFeConsultaProtocolo4">
                  <retConsSitNFe versao="4.00" xmlns="http://www.portalfiscal.inf.br/nfe">
                    <tpAmb>2</tpAmb><verAplic>RS202611271200</verAplic><cStat>100</cStat>
                    <xMotivo>Autorizado o uso da NF-e</xMotivo><cUF>43</cUF>
                    <dhRecbto>2026-11-27T15:10:00-03:00</dhRecbto>
                    <chNFe>43261111222333000181550010000001231876543212</chNFe>
                    <protNFe versao="4.00">
                      <infProt>
                        <tpAmb>2</tpAmb><verAplic>RS202611271200</verAplic>
                        <chNFe>43261111222333000181550010000001231876543212</chNFe>
                        <dhRecbto>2026-11-27T14:02:33-03:00</dhRecbto><nProt>143260000012345</nProt>
                        <digVal>AAECAwQFBgcICQoLDA0ODxAREhM=</digVal>
                        <cStat>100</cStat><xMotivo>Autorizado o uso da NF-e</xMotivo>
                      </infProt>
                    </protNFe>
                  </retConsSitNFe>
                </nfeResultMsg>
              </soap:Body>
            </soap:Envelope>
            """;

    /** Consulta protocolo de uma NF-e cancelada por evento: {@code protNFe} mais o {@code procEventoNFe}. */
    public static final String CONSULTA_CANCELADA = """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
              <soap:Body>
                <nfeResultMsg xmlns="http://www.portalfiscal.inf.br/nfe/wsdl/NFeConsultaProtocolo4">
                  <retConsSitNFe versao="4.00" xmlns="http://www.portalfiscal.inf.br/nfe">
                    <tpAmb>2</tpAmb><verAplic>RS202611271200</verAplic><cStat>101</cStat>
                    <xMotivo>Cancelamento de NF-e homologado</xMotivo><cUF>43</cUF>
                    <dhRecbto>2026-11-27T16:00:00-03:00</dhRecbto>
                    <chNFe>43261111222333000181550010000001241564738295</chNFe>
                    <protNFe versao="4.00">
                      <infProt>
                        <tpAmb>2</tpAmb><verAplic>RS202611271200</verAplic>
                        <chNFe>43261111222333000181550010000001241564738295</chNFe>
                        <dhRecbto>2026-11-27T14:20:00-03:00</dhRecbto><nProt>143260000012346</nProt>
                        <digVal>FBUWFxgZGhscHR4fICEiIyQlJic=</digVal>
                        <cStat>100</cStat><xMotivo>Autorizado o uso da NF-e</xMotivo>
                      </infProt>
                    </protNFe>
                    <procEventoNFe versao="1.00">
                      <evento versao="1.00">
                        <infEvento Id="ID1101114326111122233300018155001000000124156473829501">
                          <cOrgao>43</cOrgao><tpAmb>2</tpAmb><CNPJ>11222333000181</CNPJ>
                          <chNFe>43261111222333000181550010000001241564738295</chNFe>
                          <dhEvento>2026-11-27T15:30:00-03:00</dhEvento><tpEvento>110111</tpEvento>
                          <nSeqEvento>1</nSeqEvento><verEvento>1.00</verEvento>
                          <detEvento versao="1.00">
                            <descEvento>Cancelamento</descEvento><nProt>143260000012346</nProt>
                            <xJust>Nota emitida apenas para teste de homologacao</xJust>
                          </detEvento>
                        </infEvento>
                        <Signature xmlns="http://www.w3.org/2000/09/xmldsig#">
                          <SignedInfo>
                            <CanonicalizationMethod Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"/>
                            <SignatureMethod Algorithm="http://www.w3.org/2000/09/xmldsig#rsa-sha1"/>
                            <Reference URI="#ID1101114326111122233300018155001000000124156473829501">
                              <Transforms>
                                <Transform Algorithm="http://www.w3.org/2000/09/xmldsig#enveloped-signature"/>
                                <Transform Algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315"/>
                              </Transforms>
                              <DigestMethod Algorithm="http://www.w3.org/2000/09/xmldsig#sha1"/>
                              <DigestValue>KCkqKywtLi8wMTIzNDU2Nzg5Ojs=</DigestValue>
                            </Reference>
                          </SignedInfo>
                          <SignatureValue>AAECAwQFBgcICQoLDA0ODw==</SignatureValue>
                          <KeyInfo><X509Data><X509Certificate>AAECAwQFBgcICQoLDA0ODw==</X509Certificate></X509Data></KeyInfo>
                        </Signature>
                      </evento>
                      <retEvento versao="1.00">
                        <infEvento>
                          <tpAmb>2</tpAmb><verAplic>RS202611271200</verAplic><cOrgao>43</cOrgao>
                          <cStat>135</cStat><xMotivo>Evento registrado e vinculado a NF-e</xMotivo>
                          <chNFe>43261111222333000181550010000001241564738295</chNFe>
                          <tpEvento>110111</tpEvento><xEvento>Cancelamento registrado</xEvento>
                          <nSeqEvento>1</nSeqEvento><dhRegEvento>2026-11-27T15:30:05-03:00</dhRegEvento>
                          <nProt>143260000012347</nProt>
                        </infEvento>
                      </retEvento>
                    </procEventoNFe>
                  </retConsSitNFe>
                </nfeResultMsg>
              </soap:Body>
            </soap:Envelope>
            """;

    /** Consulta protocolo de uma chave válida que nunca foi emitida: rejeição 217, sem {@code protNFe}. */
    public static final String CONSULTA_INEXISTENTE = """
            <?xml version="1.0" encoding="utf-8"?>
            <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
              <soap:Body>
                <nfeResultMsg xmlns="http://www.portalfiscal.inf.br/nfe/wsdl/NFeConsultaProtocolo4">
                  <retConsSitNFe versao="4.00" xmlns="http://www.portalfiscal.inf.br/nfe">
                    <tpAmb>2</tpAmb><verAplic>RS202611271200</verAplic><cStat>217</cStat>
                    <xMotivo>Rejeicao: NF-e nao consta na base de dados da SEFAZ</xMotivo><cUF>43</cUF>
                    <dhRecbto>2026-11-27T15:12:00-03:00</dhRecbto>
                    <chNFe>43261111222333000181550010000009991314159268</chNFe>
                  </retConsSitNFe>
                </nfeResultMsg>
              </soap:Body>
            </soap:Envelope>
            """;
}
