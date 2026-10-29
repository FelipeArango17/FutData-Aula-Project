package co.edu.futdata.util.reports;

/**
 * Escribe números enteros en letras, en español, para los montos de los comprobantes: {@code 90000} es
 * "noventa mil" y {@link #enPesos(long)} da "noventa mil pesos M/CTE". Va de 0 a {@link #MAXIMO}.
 *
 * <p>Usa la apócope delante de un sustantivo ("un millón", "veintiún mil", "ciento un pesos") y "de" cuando
 * el monto termina en millones ("un millón de pesos").
 */
public final class NumeroEnLetras {

    /** 999.999.999.999: hasta cientos de miles de millones (no usa "billón"). */
    public static final long MAXIMO = 999_999_999_999L;

    private static final String[] HASTA_VEINTINUEVE = {
            "cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve",
            "diez", "once", "doce", "trece", "catorce", "quince", "dieciséis", "diecisiete", "dieciocho", "diecinueve",
            "veinte", "veintiuno", "veintidós", "veintitrés", "veinticuatro", "veinticinco", "veintiséis",
            "veintisiete", "veintiocho", "veintinueve"};
    private static final String[] DECENAS = {
            "", "", "", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa"};
    private static final String[] CENTENAS = {
            "", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos", "seiscientos",
            "setecientos", "ochocientos", "novecientos"};

    private NumeroEnLetras() {
    }

    /** El número en letras, sin apócope: {@code 1} es "uno" y {@code 21} es "veintiuno". */
    public static String convertir(long numero) {
        return convertir(numero, false);
    }

    /**
     * El monto en pesos colombianos: "un peso M/CTE", "noventa mil pesos M/CTE", "un millón de pesos M/CTE".
     * M/CTE: moneda corriente.
     */
    public static String enPesos(long monto) {
        if (monto == 1) {
            return "un peso M/CTE";
        }
        String texto = convertir(monto, true);
        boolean terminaEnMillones = monto > 0 && monto % 1_000_000 == 0;
        return texto + (terminaEnMillones ? " de pesos" : " pesos") + " M/CTE";
    }

    private static String convertir(long numero, boolean apocope) {
        if (numero < 0 || numero > MAXIMO) {
            throw new IllegalArgumentException("El número debe estar entre 0 y " + MAXIMO);
        }
        if (numero == 0) {
            return HASTA_VEINTINUEVE[0];
        }
        long millones = numero / 1_000_000;
        int resto = (int) (numero % 1_000_000);
        StringBuilder texto = new StringBuilder();
        if (millones == 1) {
            texto.append("un millón");
        } else if (millones > 1) {
            texto.append(miles((int) millones, true)).append(" millones");
        }
        if (resto > 0) {
            if (!texto.isEmpty()) {
                texto.append(' ');
            }
            texto.append(miles(resto, apocope));
        }
        return texto.toString();
    }

    /** De 1 a 999.999. */
    private static String miles(int numero, boolean apocope) {
        int miles = numero / 1000;
        int resto = numero % 1000;
        StringBuilder texto = new StringBuilder();
        if (miles == 1) {
            texto.append("mil");
        } else if (miles > 1) {
            texto.append(centenas(miles, true)).append(" mil");
        }
        if (resto > 0) {
            if (!texto.isEmpty()) {
                texto.append(' ');
            }
            texto.append(centenas(resto, apocope));
        }
        return texto.toString();
    }

    /** De 1 a 999. Con apócope, "uno" final pasa a "un" y "veintiuno", a "veintiún". */
    private static String centenas(int numero, boolean apocope) {
        if (numero == 100) {
            return "cien";
        }
        int centena = numero / 100;
        int resto = numero % 100;
        StringBuilder texto = new StringBuilder(CENTENAS[centena]);
        if (resto > 0) {
            if (!texto.isEmpty()) {
                texto.append(' ');
            }
            if (resto < 30) {
                texto.append(HASTA_VEINTINUEVE[resto]);
            } else {
                texto.append(DECENAS[resto / 10]);
                if (resto % 10 > 0) {
                    texto.append(" y ").append(HASTA_VEINTINUEVE[resto % 10]);
                }
            }
        }
        String resultado = texto.toString();
        if (apocope) {
            if (resultado.endsWith("veintiuno")) {
                return resultado.substring(0, resultado.length() - "veintiuno".length()) + "veintiún";
            }
            if (resultado.endsWith("uno")) {
                return resultado.substring(0, resultado.length() - 1);
            }
        }
        return resultado;
    }
}
