package com.calcforge.engine.ast;

import com.calcforge.engine.unit.PhysicalValue;
import lombok.Value;

@Value
public class PhysicalValueExpr implements Expr {
    PhysicalValue physicalValue;
}
