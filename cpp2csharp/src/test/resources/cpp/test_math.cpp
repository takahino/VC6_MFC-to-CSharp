// テスト: 数学関数 → System.Math 変換
// C 標準数学関数から C# の Math クラスへのマッピング

#include <math.h>

// ケース1: sin / cos / tan の変換
double CalcTrigonometric(double angle)
{
    double s = sin(angle);
    double c = cos(angle);
    double t = tan(angle);
    return s + c + t;
}

// ケース2: sqrt / pow の変換
double CalcPower(double base, double exp)
{
    double r = sqrt(base);
    double p = pow(base, exp);
    return r + p;
}

// ケース3: abs / fabs の変換
double CalcAbsolute(double x, int n)
{
    double fa = fabs(x);
    int ia = abs(n);
    return fa + ia;
}

// ケース4: log / log10 / floor / ceil の変換
double CalcLogarithm(double val)
{
    double ln = log(val);
    double lg = log10(val);
    double fl = floor(val);
    double cl = ceil(val);
    return ln + lg + fl + cl;
}

// ケース5: 複合式 (pow の引数が式)
double CalcHypotenuse(double x, double y)
{
    return sqrt(pow(x, 2.0) + pow(y, 2.0));
}

// ケース6: ネストした数学関数
double CalcNested(double theta)
{
    double result = sqrt(sin(theta) * sin(theta) + cos(theta) * cos(theta));
    return result;
}

// ケース7: 変数式を引数にした pow
double CalcComplexPower(double a, double b, double c)
{
    return pow(a + b, c - 1.0);
}
