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
}
